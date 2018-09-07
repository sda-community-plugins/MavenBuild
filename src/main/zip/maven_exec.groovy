#!/usr/bin/env groovy

final String lineSep = System.getProperty('line.separator')
final String osName = System.getProperty('os.name').toLowerCase(Locale.US)
final String pathSep = System.getProperty('path.separator')
final boolean windows = (osName =~ /windows/)
final boolean vms = (osName =~ /vms/)
final boolean os9 = (osName =~ /mac/ && !osName.endsWith('x'))
final boolean unix = (pathSep == ':' && !vms && !os9)

final def workDir = new File('.').canonicalFile
final def props = new Properties();
final def inputPropsFile = new File(args[0]);
final def inputPropsStream = null;
try {
    inputPropsStream = new FileInputStream(inputPropsFile);
    props.load(inputPropsStream);
}
catch (IOException e) {
    throw new RuntimeException(e);
}
finally {
    inputPropsStream.close();
}

final def directoryOffset = props['directoryOffset']
final def MAVEN_HOME = props['mvnHome']?.trim()
final def mvnPom = props['pomFile']
final def mvnGoals = props['mvnGoals']
final def mvnOptions = props['mvnOptions']
final def mvnProperties = props['mvnProperties']
final boolean injectProperties = props['injectProperties']
final def mvnSettings = props['mvnSettings']
final def JAVA_HOME = props['javaHome']
final def jvmProperties = props['jvmProperties']
final boolean verbose = props['verbose']
final def envVars = props['envVars']

//
// Validation
//

if (directoryOffset) {
    workDir = new File(workDir, directoryOffset).canonicalFile
}

if (workDir.isFile()) {
    throw new IllegalArgumentException("Working directory ${workDir} is a file!")
}

if (mvnPom == null) {
    println("Maven POM not specified, using pom.xml.");
    mvnPom = "pom.xml"
}

if (MAVEN_HOME == null) {
    throw new IllegalStateException("MAVEN_HOME not specified");
}

if (JAVA_HOME == null) {
    throw new IllegalStateException("JAVA_HOME not specified");
}

// ensure work-dir exists
workDir.mkdirs()

final def mvnFile = new File(workDir, mvnPom)

int exitCode = -1;
try {
    def mvnExe = new File(MAVEN_HOME, "bin/mvn" + (windows ? ".cmd" : ""))

    def MAVEN_OPTS = ""

    //
    // Build Command Line
    //
    def commandLine = []
    commandLine.add(mvnExe.absolutePath)

    commandLine.add("-f")
    commandLine.add(mvnFile.absolutePath)

    if (mvnGoals) {
        mvnGoals.split("\\s").each() { goalName ->
            if (goalName) {
                commandLine.add(goalName)
            }
        }
    }

    if (mvnOptions) {
        mvnOptions.split("\\s").each() { optionName ->
            if (optionName) {
                commandLine.add(optionName)
            }
        }
    }

    if (mvnProperties) {
        mvnProperties.split("\\s").each() { property ->
            if (property) {
                if (!property.startsWith("-D")) {
                    property = "-D" + property
                }
                commandLine.add(property)
            }
        }
    }

    if (jvmProperties) {
        jvmProperties.split("\\s").each() { property ->
            if (property) {
                MAVEN_OPTS += property + " "
            }
        }
        if (verbose) println("setting MAVEN_OPTS: '${MAVEN_OPTS}'")
    }

    if (injectProperties) {
        envVars.split("(?<=(^|[^\\\\])(\\\\{2}){0,8}),").each { prop ->
            //split out the name
            def parts = prop.split("(?<=(^|[^\\\\])(\\\\{2}){0,8})=", 2);
            def propName = parts[0];
            def propValue = parts.size() == 2 ? parts[1] : "";
            //replace \, with just , and then \\ with \
            propName = propName.replace("\\=", "=").replace("\\,", ",").replace("\\\\", "\\")
            propValue = propValue.replace("\\=", "=").replace("\\,", ",").replace("\\\\", "\\")
            def property = "-D" + propName + '=' + propValue
            commandLine.add(property)
            if (verbose) println("setting variable from DA environment: '${propName}=${propValue}'")
        }
    }

    if (mvnSettings) {
        commandLine.add("-s")
        commandLine.add(mvnSettings)
    }


    // print out command info
    println("")
    println("command line: ${commandLine.join(' ')}")
    println("working directory: ${workDir.path}")
    println('===============================')
    println("command output: ")

    //
    // Launch Process
    //
    final def processBuilder = new ProcessBuilder(commandLine as String[]).directory(workDir)
    processBuilder.environment().put("MAVEN_HOME", MAVEN_HOME)
    processBuilder.environment().put("JAVA_HOME", JAVA_HOME)
    if (MAVEN_OPTS) processBuilder.environment().put("MAVEN_OPTS", MAVEN_OPTS)
    final def process = processBuilder.start()
    process.out.close() // close stdin
    process.waitForProcessOutput(System.out, System.err) // forward stdout and stderr
    process.waitFor()

    // print results
    println('===============================')
    println("command exit code: ${process.exitValue()}")
    println("")

    exitCode = process.exitValue();
}
finally {

}
System.exit(exitCode);
