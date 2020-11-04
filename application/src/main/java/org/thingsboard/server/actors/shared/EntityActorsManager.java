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
package org.thingsboard.server.actors.shared;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.actors.ActorSystemContext;
import org.thingsboard.server.actors.service.ContextAwareActor;
import org.thingsboard.server.common.data.SearchTextBased;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UUIDBased;
import org.thingsboard.server.common.data.page.PageDataIterable;

/**
 * Created by ashvayka on 15.03.18.
 */
@Slf4j
public abstract class EntityActorsManager<T extends EntityId, A extends UntypedActor, M extends SearchTextBased<? extends UUIDBased>> {

    protected final ActorSystemContext systemContext;
    protected final BiMap<T, ActorRef> actors;

    public EntityActorsManager(ActorSystemContext systemContext) {
        this.systemContext = systemContext;
        this.actors = HashBiMap.create();
    }

    protected abstract TenantId getTenantId();

    protected abstract String getDispatcherName();

    //抽象方法，由继承该类的子类实现
    protected abstract Creator<A> creator(T entityId);

    protected abstract PageDataIterable.FetchFunction<M> getFetchEntitiesFunction();

    public void init(ActorContext context) {
        for (M entity : new PageDataIterable<>(getFetchEntitiesFunction(), ContextAwareActor.ENTITY_PACK_LIMIT)) {
            T entityId = (T) entity.getId();
            log.debug("[{}|{}] Creating entity actor", entityId.getEntityType(), entityId.getId());
            //TODO: remove this cast making UUIDBased subclass of EntityId an interface and vice versa.
            //创建一个RuleChainActor
            ActorRef actorRef = getOrCreateActor(context, entityId);
            //RuleChainManager实现该抽象类EntityActorsManager的visit(),给RuleChainManager类的两个属性赋值
            visit(entity, actorRef);
            log.debug("[{}|{}] Entity actor created.", entityId.getEntityType(), entityId.getId());
        }
    }

    public void visit(M entity, ActorRef actorRef) {
    }

    /**
     * 根据context以及entityId创建一个ruleChainActor
     * @param context
     * @param entityId
     * @return
     */
    public ActorRef getOrCreateActor(ActorContext context, T entityId) {
        //BiMap<T, ActorRef> actors
        //creator(eId)：创建一个Creator<RuleChainActor>
        return actors.computeIfAbsent(entityId, eId ->
                context.actorOf(Props.create(creator(eId))
                        .withDispatcher(getDispatcherName()), eId.toString()));
    }

    public void broadcast(Object msg) {
        actors.values().forEach(actorRef -> actorRef.tell(msg, ActorRef.noSender()));
    }

    public void remove(T id) {
        actors.remove(id);
    }

}
