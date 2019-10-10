// --------------------------------------------------------------------------------
// Execute a Maven Build command
// --------------------------------------------------------------------------------

import com.serena.air.StepFailedException
import com.serena.air.StepPropertiesHelper
import com.serena.air.TextAreaParser
import com.urbancode.air.AirPluginTool

//
// Create some variables that we can use throughout the plugin step.
// These are mainly for checking what operating system we are running on.
//
final def PLUGIN_HOME = System.getenv()['PLUGIN_HOME']
final String lineSep = System.getProperty('line.separator')
final String osName = System.getProperty('os.name').toLowerCase(Locale.US)
final String pathSep = System.getProperty('path.separator')
final boolean windows = (osName =~ /windows/)
final boolean vms = (osName =~ /vms/)
final boolean os9 = (osName =~ /mac/ && !osName.endsWith('x'))
final boolean unix = (pathSep == ':' && !vms && !os9)

//
// Initialise the plugin tool and retrieve all the properties that were sent to the step.
//
final def  apTool = new AirPluginTool(this.args[0], this.args[1])
final def  props  = new StepPropertiesHelper(apTool.getStepProperties(), true)

//
// Set a variable for each of the plugin steps's inputs.
// We can check whether a required input is supplied (the helper will fire an exception if not) and
// if it is of the required type.
//
File workDir = new File('.').canonicalFile
String directoryOffset = props.optional('directoryOffset')
String mvnHome = props.notNull('mvnHome')
String mvnPom = props.optional('mvnPom', "pom.xml")
String mvnGoals = props.optional('mvnGoals')
String mvnOptions = props.optional('mvnOptions')
String mvnProperties = props.optional('mvnProperties')
Boolean injectProperties = props.optionalBoolean('injectProperties', false)
String mvnSettings = props.optional('mvnSettings')
String javaHome = props.notNull('javaHome')
String jvmProperties = props.optional('jvmProperties')
Boolean debugMode = props.optionalBoolean("debugMode", false)
String envVars = props.optional('envVars')

println "----------------------------------------"
println "-- STEP INPUTS"
println "----------------------------------------"

//
// Print out each of the property values.
//
println "Working directory: ${workDir.canonicalPath}"
println "Directory Offset: ${directoryOffset}"
println "MAVEN_HOME: ${mvnHome}"
println "Root POM: ${mvnPom}"
println "Goals: ${mvnGoals}"
println "Options: ${mvnOptions}"
println "Properties: ${mvnProperties}"
println "Inject Environment Properties: ${injectProperties}"
println "User Settings Fil: ${mvnSettings}"
println "JAVA_HOME: ${javaHome}"
println "JVM Properties: ${jvmProperties}"
println "Debug Output: ${debugMode}"
if (debugMode) { props.setDebugLoggingMode() }

int exitCode = -1;

//
// The main body of the plugin step - wrap it in a try/catch statement for handling any exceptions.
//
try {

    //
// Validation
//

    if (directoryOffset) {
        workDir = new File(workDir, directoryOffset).canonicalFile
    }

    if (workDir.isFile()) {
        throw new StepFailedException("Working directory ${workDir} is a file!")
    }

    if (mvnPom == null) {
        println("Maven POM not specified, using pom.xml.");
        mvnPom = "pom.xml"
    }

    if (mvnHome== null) {
        throw new StepFailedException("MAVEN_HOME not specified");
    }

    if (javaHome == null) {
        throw new StepFailedException("JAVA_HOME not specified");
    }

    // ensure work-dir exists
    workDir.mkdirs()

    final def mvnFile = new File(workDir, mvnPom)

    def mvnExe = new File(mvnHome, "bin/mvn" + (windows ? ".cmd" : ""))

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
        if (debugMode) println("setting MAVEN_OPTS: '${MAVEN_OPTS}'")
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
            if (debugMode) println("setting variable from DA environment: '${propName}=${propValue}'")
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
    processBuilder.environment().put("MAVEN_HOME", mvnHome)
    processBuilder.environment().put("JAVA_HOME", javaHome)
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
} catch (StepFailedException e) {
    //
    // Catch any exceptions we find and print their details out.
    //
    println "ERROR: ${e.message}"
    // An exit with a non-zero value will be deemed a failure
    System.exit 1
}

//
// An exit with a zero value means the plugin step execution will be deemed successful.
//
System.exit(exitCode);
