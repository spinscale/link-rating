/*
 * Copyright [2020] [Alexander Reelsen]
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
 *
 */

package de.spinscale.linkrating;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AdminService implements Supplier<List<String>> {

    private final List<String> admins;

    public AdminService(String ... users) {
        if (users.length == 0) {
            final String[] admins = System.getenv("ADMINS").split(",");
            this.admins = Arrays.stream(admins).map(String::trim).collect(Collectors.toList());
        } else {
            this.admins = Arrays.asList(users);
        }
    }

    @Override
    public List<String> get() {
        return admins;
    }
}
