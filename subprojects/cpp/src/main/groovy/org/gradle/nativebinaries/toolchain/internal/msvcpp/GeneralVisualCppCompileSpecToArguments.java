/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.nativebinaries.toolchain.internal.msvcpp;

import org.gradle.api.internal.tasks.compile.ArgCollector;
import org.gradle.api.internal.tasks.compile.CompileSpecToArguments;
import org.gradle.nativebinaries.toolchain.internal.MacroArgsConverter;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;

import java.io.File;

class GeneralVisualCppCompileSpecToArguments<T extends NativeCompileSpec> implements CompileSpecToArguments<T>  {
    public void collectArguments(T spec, ArgCollector collector) {
        collector.args("/nologo");

        for (String macroArg : new MacroArgsConverter().transform(spec.getMacros())) {
            collector.args("/D" + macroArg);
        }
        collector.args(spec.getArgs());
        collector.args("/c");
        for (File file : spec.getIncludeRoots()) {
            collector.args("/I", file.getAbsolutePath());
        }
        for (File file : spec.getSource()) {
            collector.args(file);
        }
    }

}
