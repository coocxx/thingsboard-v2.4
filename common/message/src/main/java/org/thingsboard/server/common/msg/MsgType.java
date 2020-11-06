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
package org.thingsboard.server.common.msg;

/**
 * Created by ashvayka on 15.03.18.
 */
//TODO: add all "See" references
public enum MsgType {

    /**
     * ADDED/UPDATED/DELETED events for server nodes.
     *为服务器节点添加/更新/删除事件。
     * See {@link org.thingsboard.server.common.msg.cluster.ClusterEventMsg}
     */
    CLUSTER_EVENT_MSG,

    APP_INIT_MSG,

    /**
     * All messages, could be send  to cluster
     * 所有消息，都可以发送到集群
    */
    SEND_TO_CLUSTER_MSG,

    /**
     * ADDED/UPDATED/DELETED events for main entities.
     *为主要实体添加/更新/删除事件。
     * See {@link org.thingsboard.server.common.msg.plugin.ComponentLifecycleMsg}
     */
    COMPONENT_LIFE_CYCLE_MSG,

    /**
     * Misc messages from the REST API/SERVICE layer to the new rule engine.
     *从RESTAPI/SERVICE层到新规则引擎的其他消息。
     * See {@link org.thingsboard.server.common.msg.system.ServiceToRuleEngineMsg}
     */
    SERVICE_TO_RULE_ENGINE_MSG,

    /**
     * Message that is sent by RuleChainActor to RuleActor with command to process TbMsg.
     * RuleChainActor向RuleActor发送的消息，其中包含处理TbMsg的命令。
     */
    RULE_CHAIN_TO_RULE_MSG,

    /**
     * Message that is sent by RuleChainActor to other RuleChainActor with command to process TbMsg.
     * RuleChainActor发送给其他RuleChainActor的TbMsg消息。
     */
    RULE_CHAIN_TO_RULE_CHAIN_MSG,

    /**
     * Message that is sent by RuleActor to RuleChainActor with command to process TbMsg by next nodes in chain.
     * 由RuleActor发送给RuleChainActor的消息，其中包含由链中的下一个节点处理TbMsg的命令。
     */
    RULE_TO_RULE_CHAIN_TELL_NEXT_MSG,

    /**
     * Message forwarded from original rule chain to remote rule chain due to change in the cluster structure or originator entity of the TbMsg.
     * 由于TbMsg的群集结构或原始实体发生更改，从原始规则链转发到远程规则链的消息。
     */
    REMOTE_TO_RULE_CHAIN_TELL_NEXT_MSG,

    /**
     * Message that is sent by RuleActor implementation to RuleActor itself to log the error.
     * 由RuleActor实现发送到RuleActor本身以记录错误的消息。
     */
    RULE_TO_SELF_ERROR_MSG,

    /**
     * Message that is sent by RuleActor implementation to RuleActor itself to process the message.
     * 由RuleActor实现发送给RuleActor本身以处理消息的消息。
     */
    RULE_TO_SELF_MSG,

    DEVICE_ATTRIBUTES_UPDATE_TO_DEVICE_ACTOR_MSG,

    DEVICE_CREDENTIALS_UPDATE_TO_DEVICE_ACTOR_MSG,

    DEVICE_NAME_OR_TYPE_UPDATE_TO_DEVICE_ACTOR_MSG,

    DEVICE_RPC_REQUEST_TO_DEVICE_ACTOR_MSG,

    SERVER_RPC_RESPONSE_TO_DEVICE_ACTOR_MSG,

    DEVICE_ACTOR_SERVER_SIDE_RPC_TIMEOUT_MSG,

    DEVICE_ACTOR_CLIENT_SIDE_RPC_TIMEOUT_MSG,

    /**
     * Message that is sent from the Device Actor to Rule Engine. Requires acknowledgement
     * 从设备参与者发送到规则引擎的消息。需要确认
     */
    DEVICE_ACTOR_TO_RULE_ENGINE_MSG,

    SESSION_TIMEOUT_MSG,

    STATS_PERSIST_TICK_MSG,


    /**
     * Message that is sent by TransportRuleEngineService to Device Actor. Represents messages from the device itself.
     * 由TransportRuleEngineService发送到设备参与者的消息。表示来自设备本身的消息。
     */
    TRANSPORT_TO_DEVICE_ACTOR_MSG;

}
