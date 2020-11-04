/**
 * Copyright © 2016-2020 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.actors.app;

import akka.actor.*;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.thingsboard.server.actors.ActorSystemContext;
import org.thingsboard.server.actors.ruleChain.RuleChainManagerActor;
import org.thingsboard.server.actors.service.ContextBasedCreator;
import org.thingsboard.server.actors.service.DefaultActorService;
import org.thingsboard.server.actors.shared.rulechain.SystemRuleChainManager;
import org.thingsboard.server.actors.tenant.TenantActor;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageDataIterable;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.msg.MsgType;
import org.thingsboard.server.common.msg.TbActorMsg;
import org.thingsboard.server.common.msg.aware.TenantAwareMsg;
import org.thingsboard.server.common.msg.cluster.SendToClusterMsg;
import org.thingsboard.server.common.msg.cluster.ServerAddress;
import org.thingsboard.server.common.msg.plugin.ComponentLifecycleMsg;
import org.thingsboard.server.common.msg.system.ServiceToRuleEngineMsg;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.tenant.TenantService;
import scala.concurrent.duration.Duration;

import java.util.Optional;

/**
 *
 */
public class AppActor extends RuleChainManagerActor {

    private static final TenantId SYSTEM_TENANT = new TenantId(ModelConstants.NULL_UUID);
    private final TenantService tenantService;
    private final BiMap<TenantId, ActorRef> tenantActors;
    private boolean ruleChainsInitialized;

    private AppActor(ActorSystemContext systemContext) {
        super(systemContext, new SystemRuleChainManager(systemContext));
        this.tenantService = systemContext.getTenantService();
        this.tenantActors = HashBiMap.create();
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        //策略
        return strategy;
    }


    @Override
    public void preStart() {
        //AppActor启动后接收到第一条消息之前执行
    }

    @Override
    protected boolean process(TbActorMsg msg) {
        if (!ruleChainsInitialized) {
            //初始化RuleChainManager
            //创建一个RuleChainActor及给租户列表创建一个TenantActor
            initRuleChainsAndTenantActors();
            ruleChainsInitialized = true;
            if (msg.getMsgType() != MsgType.APP_INIT_MSG) {
                //未初始化
                log.warn("Rule Chains initialized by unexpected message: {}", msg);
            }
        }
        //根据消息类型的不同而进行不同的操作
        switch (msg.getMsgType()) {
            case APP_INIT_MSG:
                break;
            case SEND_TO_CLUSTER_MSG:
                //address有效存在：将消息发送给rpcManagerActor,消息类型为ClusterAPIProtos.ClusterMessage CLUSTER_ACTOR_MESSAGE
                //address不存在：将消息发送给当前appActor,消息类型为MsgType.SEND_TO_CLUSTER_MSG
                onPossibleClusterMsg((SendToClusterMsg) msg);
                break;
            case CLUSTER_EVENT_MSG:
                //向已经存在的每一个RuleChainActor及每一个TenantActor发送一条消息，消息类型TbActorMsg
                broadcast(msg);
                break;
            case COMPONENT_LIFE_CYCLE_MSG:
                onComponentLifecycleMsg((ComponentLifecycleMsg) msg);
                break;
            case SERVICE_TO_RULE_ENGINE_MSG:
                onServiceToRuleEngineMsg((ServiceToRuleEngineMsg) msg);
                break;
            case TRANSPORT_TO_DEVICE_ACTOR_MSG:
            case DEVICE_ATTRIBUTES_UPDATE_TO_DEVICE_ACTOR_MSG:
            case DEVICE_CREDENTIALS_UPDATE_TO_DEVICE_ACTOR_MSG:
            case DEVICE_NAME_OR_TYPE_UPDATE_TO_DEVICE_ACTOR_MSG:
            case DEVICE_RPC_REQUEST_TO_DEVICE_ACTOR_MSG:
            case SERVER_RPC_RESPONSE_TO_DEVICE_ACTOR_MSG:
            case REMOTE_TO_RULE_CHAIN_TELL_NEXT_MSG:
                onToDeviceActorMsg((TenantAwareMsg) msg);
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * 初始化RuleChainManager
     * 创建一个RuleChainActor及给每一个租户创建一个TenantActor
     */
    private void initRuleChainsAndTenantActors() {
        log.info("Starting main system actor.");
        try {
            //初始化RuleChainManager
            //1.创建一个RuleChainActor
            //2.visit(),给RuleChainManager类的两个属性赋值
            initRuleChains();
            if (systemContext.isTenantComponentsInitEnabled()) {
                //创建一个PageDataIterable类的实例,用于遍历
                //tenantService::findTenants 根据Region查找tenant然后返回tenant列表tenants,然后通过列表tenants以及pageLink参数创建实例TextPageData，返回TextPageData<Tenant>
                PageDataIterable<Tenant> tenantIterator = new PageDataIterable<>(tenantService::findTenants, ENTITY_PACK_LIMIT);
                for (Tenant tenant : tenantIterator) {
                    log.debug("[{}] Creating tenant actor", tenant.getId());
                    //创建一个TenantActor实例
                    getOrCreateTenantActor(tenant.getId());
                    log.debug("Tenant actor created.");
                }
            }
            log.info("Main system actor started.");
        } catch (Exception e) {
            log.warn("Unknown failure", e);
        }
    }

    private void onPossibleClusterMsg(SendToClusterMsg msg) {
        //routingService.resolveById(msg.getEntityId())  Optional<ServerAddress>
        Optional<ServerAddress> address = systemContext.getRoutingService().resolveById(msg.getEntityId());
        if (address.isPresent()) {
            //将消息发送给rpcManagerActor,消息类型为ClusterAPIProtos.ClusterMessage CLUSTER_ACTOR_MESSAGE
            systemContext.getRpcService().tell(
                    systemContext.getEncodingService().convertToProtoDataMessage(address.get(), msg.getMsg()));
        } else {
            //将消息发送给当前appActor,消息类型为MsgType.SEND_TO_CLUSTER_MSG
            self().tell(msg.getMsg(), ActorRef.noSender());
        }
    }

    private void onServiceToRuleEngineMsg(ServiceToRuleEngineMsg msg) {
        if (SYSTEM_TENANT.equals(msg.getTenantId())) {
//            this may be a notification about system entities created.
//            log.warn("[{}] Invalid service to rule engine msg called. System messages are not supported yet: {}", SYSTEM_TENANT, msg);
        } else {
            getOrCreateTenantActor(msg.getTenantId()).tell(msg, self());
        }
    }

    @Override
    protected void broadcast(Object msg) {
        //向已经存在的每一个RuleChainActor发送一条消息，消息类型TbActorMsg
        super.broadcast(msg);
        //向已经存在的每一个TenantActor发送一条消息，消息类型TbActorMsg
        tenantActors.values().forEach(actorRef -> actorRef.tell(msg, ActorRef.noSender()));
    }

    private void onComponentLifecycleMsg(ComponentLifecycleMsg msg) {
        ActorRef target = null;
        if (SYSTEM_TENANT.equals(msg.getTenantId())) {
            target = getEntityActorRef(msg.getEntityId());
        } else {
            if (msg.getEntityId().getEntityType() == EntityType.TENANT
                    && msg.getEvent() == ComponentLifecycleEvent.DELETED) {
                log.debug("[{}] Handling tenant deleted notification: {}", msg.getTenantId(), msg);
                ActorRef tenantActor = tenantActors.remove(new TenantId(msg.getEntityId().getId()));
                if (tenantActor != null) {
                    log.debug("[{}] Deleting tenant actor: {}", msg.getTenantId(), tenantActor);
                    context().stop(tenantActor);
                }
            } else {
                target = getOrCreateTenantActor(msg.getTenantId());
            }
        }
        if (target != null) {
            target.tell(msg, ActorRef.noSender());
        } else {
            log.debug("[{}] Invalid component lifecycle msg: {}", msg.getTenantId(), msg);
        }
    }

    private void onToDeviceActorMsg(TenantAwareMsg msg) {
        getOrCreateTenantActor(msg.getTenantId()).tell(msg, ActorRef.noSender());
    }

    /**
     * 创建一个TenantActor实例
     * @param tenantId
     * @return
     */
    private ActorRef getOrCreateTenantActor(TenantId tenantId) {
        return tenantActors.computeIfAbsent(tenantId, k -> {
            log.debug("[{}] Creating tenant actor.", tenantId);
            ActorRef tenantActor = context().actorOf(Props.create(new TenantActor.ActorCreator(systemContext, tenantId))
                    .withDispatcher(DefaultActorService.CORE_DISPATCHER_NAME), tenantId.toString());
            context().watch(tenantActor);
            log.debug("[{}] Created tenant actor: {}.", tenantId, tenantActor);
            return tenantActor;
        });
    }

    @Override
    protected void processTermination(Terminated message) {
        ActorRef terminated = message.actor();
        if (terminated instanceof LocalActorRef) {
            boolean removed = tenantActors.inverse().remove(terminated) != null;
            if (removed) {
                log.debug("[{}] Removed actor:", terminated);
            }
        } else {
            throw new IllegalStateException("Remote actors are not supported!");
        }
    }

    public static class ActorCreator extends ContextBasedCreator<AppActor> {
        private static final long serialVersionUID = 1L;

        public ActorCreator(ActorSystemContext context) {
            super(context);
        }

        @Override
        public AppActor create() {
            return new AppActor(context);
        }
    }

    /**
     * 在akka中，每个actor都是其子actor的supervisor。当一个子actor失败时，supervisor有两种策略：
     *
     * OneForOneStrategy 只针对异常的那个子actor操作
     * OneForAllStrategy 对所有子actor操作
     * rest_for_one：针对一个子进程列表，一个子进程停止，停止列表中该子进程及后面的子进程，并依次重启这些子进程
     * simple_one_for_one：其重启策略同one_for_one,但是必须是同类型的子进程，必须动态加入。
     *
     * 可选的行为有Resume恢复 Restart重新启动 Stop停止 Escalate升级
     */
    private final SupervisorStrategy strategy = new OneForOneStrategy(3, Duration.create("1 minute"), t -> {
        log.warn("Unknown failure", t);
        if (t instanceof RuntimeException) {
            return SupervisorStrategy.restart();
        } else {
            return SupervisorStrategy.stop();
        }
    });
}
