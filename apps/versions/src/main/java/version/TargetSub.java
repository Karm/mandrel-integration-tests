/*
 * Copyright (c) 2021, Red Hat Inc. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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
package version;

import java.util.function.BooleanSupplier;

import org.graalvm.home.Version;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * @author Severin Gehwolf <sgehwolf@redhat.com>
 */
@TargetClass(value = Sub.class, onlyWith = GraalVM20OrLater.class)
@SuppressWarnings({ "unused" })
public final class TargetSub {

    @Substitute
    public void run() {
       System.out.println("TargetSub: Hello!");
    }

}
