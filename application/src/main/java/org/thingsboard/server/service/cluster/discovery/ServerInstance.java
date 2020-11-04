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
package org.thingsboard.server.service.cluster.discovery;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.thingsboard.server.common.msg.cluster.ServerAddress;

/**
 * @EqualsAndHashCode
 * 1. 此注解会生成equals(Object other) 和 hashCode()方法。
 * 2. 它默认使用非静态，非瞬态的属性
 * 3. 可通过参数exclude排除一些属性
 * 4. 可通过参数of指定仅使用哪些属性
 * 5. 它默认仅使用该类中定义的属性且不调用父类的方法
 * 6. 可通过callSuper=true解决上一点问题。让其生成的方法中调用父类的方法。
 */
@ToString
@EqualsAndHashCode(exclude = {"serverInfo", "serverAddress"})
public final class ServerInstance implements Comparable<ServerInstance> {

    @Getter
    private final String host;
    @Getter
    private final int port;
    @Getter
    private final ServerAddress serverAddress;

    public ServerInstance(ServerAddress serverAddress) {
        this.serverAddress = serverAddress;
        this.host = serverAddress.getHost();
        this.port = serverAddress.getPort();
    }

    @Override
    public int compareTo(ServerInstance o) {
        return this.serverAddress.compareTo(o.serverAddress);
    }
}
