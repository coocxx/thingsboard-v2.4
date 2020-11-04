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
package org.thingsboard.server.actors.ruleChain;

import akka.actor.OneForOneStrategy;
import akka.actor.SupervisorStrategy;
import org.thingsboard.server.actors.ActorSystemContext;
import org.thingsboard.server.actors.device.DeviceActorToRuleEngineMsg;
import org.thingsboard.server.actors.service.ComponentActor;
import org.thingsboard.server.actors.service.ContextBasedCreator;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.TbActorMsg;
import org.thingsboard.server.common.msg.plugin.ComponentLifecycleMsg;
import org.thingsboard.server.common.msg.system.ServiceToRuleEngineMsg;
import scala.concurrent.duration.Duration;

public class RuleChainActor extends ComponentActor<RuleChainId, RuleChainActorMessageProcessor> {

    private RuleChainActor(ActorSystemContext systemContext, TenantId tenantId, RuleChainId ruleChainId) {
        super(systemContext, tenantId, ruleChainId);
        setProcessor(new RuleChainActorMessageProcessor(tenantId, ruleChainId, systemContext,
                context().parent(), context().self()));
    }

    @Override
    protected boolean process(TbActorMsg msg) {
        switch (msg.getMsgType()) {
            case COMPONENT_LIFE_CYCLE_MSG:
                onComponentLifecycleMsg((ComponentLifecycleMsg) msg);
                break;
            case SERVICE_TO_RULE_ENGINE_MSG:
                processor.onServiceToRuleEngineMsg((ServiceToRuleEngineMsg) msg);
                break;
            case DEVICE_ACTOR_TO_RULE_ENGINE_MSG:
                processor.onDeviceActorToRuleEngineMsg((DeviceActorToRuleEngineMsg) msg);
                break;
            case RULE_TO_RULE_CHAIN_TELL_NEXT_MSG:
            case REMOTE_TO_RULE_CHAIN_TELL_NEXT_MSG:
                processor.onTellNext((RuleNodeToRuleChainTellNextMsg) msg);
                break;
            case RULE_CHAIN_TO_RULE_CHAIN_MSG:
                processor.onRuleChainToRuleChainMsg((RuleChainToRuleChainMsg) msg);
                break;
            case CLUSTER_EVENT_MSG:
                break;
            case STATS_PERSIST_TICK_MSG:
                onStatsPersistTick(id);
                break;
            default:
                return false;
        }
        return true;
    }

    public static class ActorCreator extends ContextBasedCreator<RuleChainActor> {
        private static final long serialVersionUID = 1L;

        private final TenantId tenantId;
        private final RuleChainId ruleChainId;

        /**
         * 构造方法
         * @param context
         * @param tenantId
         * @param pluginId
         */
        public ActorCreator(ActorSystemContext context, TenantId tenantId, RuleChainId pluginId) {
            super(context);
            this.tenantId = tenantId;
            this.ruleChainId = pluginId;
        }

        /**
         * 创建一个RuleChainActor实例并返回
         * @return RuleChainActor
         */
        @Override
        public RuleChainActor create() {
            return new RuleChainActor(context, tenantId, ruleChainId);
        }
    }

    @Override
    protected long getErrorPersistFrequency() {
        return systemContext.getRuleChainErrorPersistFrequency();
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    /**
     * 在akka中，每个actor都是其子actor的supervisor。当一个子actor失败时，supervisor有两种策略：
     *
     * OneForOneStrategy 只针对异常的那个子actor操作
     * OneForAllStrategy 对所有子actor操作
     * rest_for_one：针对一个子进程列表，一个子进程停止，停止列表中该子进程及后面的子进程，并依次重启这些子进程
     * simple_one_for_one：其重启策略同one_for_one,但是必须是同类型的子进程，必须动态加入。
     *
     * 可选的行为有Resume恢复 Restart重新启动 Stop停止 Escalate升级。
     */
    private final SupervisorStrategy strategy = new OneForOneStrategy(3, Duration.create("1 minute"), t -> {
        logAndPersist("Unknown Failure", ActorSystemContext.toException(t));
        return SupervisorStrategy.resume();
    });
}
