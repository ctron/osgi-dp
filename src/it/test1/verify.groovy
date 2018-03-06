/*******************************************************************************
 * Copyright (c) 2018 Jens Reimann
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann <jreimann@redhat.com> - initial API and implementation
 *******************************************************************************/

def zipFile = new java.util.zip.ZipFile(new File(basedir, "test1-feature1/target/test1.feature1_1.0.0-SNAPSHOT.dp"))

def entries = new HashSet<String>();
zipFile
        .entries()
        .each{
            entries.add(it.name.replaceAll("(1\\.0\\.0)\\.\\d{12}(\\.jar)", "\$1\$2"))
        };

println("Found: " + entries)

return entries.equals(
        [
            "META-INF/MANIFEST.MF",
            "test1.bundle1_1.0.0.jar",
            "com.google.gson_2.2.4.v201311231704.jar",
            "ch.qos.logback.core_1.0.7.v20121108-1250.jar"
        ]
        .toSet());