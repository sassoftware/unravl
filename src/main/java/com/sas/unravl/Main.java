package com.sas.unravl;

import com.sas.unravl.ui.UnRAVLFrame;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Properties;

/**
 * The main command-line interface for running {@link UnRAVL} scripts. You can
 * run via <code>bin/unravl.sh</code> (Linux, Mac OS X) or
 * <code>bin\\unravl.bat</code> (Windows) Below, <code>unravl</code> refers to
 * the correct script for you environment
 * 
 * <pre>
 * unravl script-file [... script-file]
 * </pre>
 *
 * Each argument on the command line names an UnRAVL script file Each such file
 * may contain the JSON representation of a script, or a script suite, which is
 * a JSON array of UnRAVL scripts.
 * <p>
 * By default, unravl has a built-in Log4j configuration file, which has tracing
 * enabled for the com.sas.unravl package, but you can pass your own by setting
 * UNRAVL_OPT:
 *
 * <pre>
 * UNRAVL_OPT="-Djog4j.configuration=mylog4j.properties"  unravl script-file
 * </pre>
 *
 * You can also use this UNRAVL_OPT if you want to pass initial variable
 * bindings for the {@link UnRAVLRuntime} environment. See the unravl.sh
 *
 * <pre>
 * UNRAVL_OPT="-Dvar1=value1 -Dvar2=value2" unravl script.json
 * </pre>
 *
 * @author David.Biesack@sas.com
 */
public final class Main {

    /**
     * Man entry point. Each command line argument <em>script-file</em> is the
     * name of an UnRAVL script file or URL. All scripts will run in the same
     * shared UnRAVLRuntime and thus share a common environment and set of
     * variables.
     * 
     * @param argv
     *            commmand line arguments
     */
    public static void main(String argv[]) {
        argv = preProcessArgs(argv);
        rerouteStdoutStderr(); // do this before starting Log4J!
        configureLog4j();
        UnRAVLRuntime.configure();
        if (ui) {
            javax.swing.JFrame frame = UnRAVLFrame.main(redirectOutput);
            frame.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        } else {
            int rc = new Main().run(argv);
            System.exit(rc);
        }
    }

    static boolean ui = false;
    static boolean redirectOutput = true;

    // Scan for --v | -verbose | -q | --quiet | --stdout and set the log4j configuration
    // remove those args from the arg list and return the remainder
    // Print help and exit on -h | --help options.
    private static String[] preProcessArgs(String[] argv) {
        ArrayList<String> args = new ArrayList<String>();
        String log4j = null;
        ui = true;
        for (String arg : argv) {
            if (arg.trim().length() == 0) // Ignore "" on command line
                continue;
            else if (arg.matches("^--?h(elp)?"))
                usage();
            else if (arg.matches("^--?q(uiet)?"))
                log4j = "log4j-quiet.properties";
            else if (arg.matches("^--?v(erbose)?"))
                log4j = "log4j-trace.properties";
            else if (arg.matches("^--?stdout"))
                redirectOutput = false;
            else {
                args.add(arg);
                ui = false;
            }
        }
        if (log4j != null)
            System.setProperty("log4j.configuration", log4j);
        return args.toArray(new String[args.size()]);
    }
    
    private static void usage() {
        System.out.println("UnRAVL - Uniform REST API Validation Language");
        System.out.println("Runs one or more UnRAVL script files, which are JSON executable REST API validation specifications.");
        System.out.println("");
        System.out.println("Synopsis:");
        System.out.println("");
        System.out.println("    unravl.sh [-q|--quiet|-v|--verbose|-h|--help] <script-file>");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("");
        System.out.println("    unravl.sh --verbose hello.json");
        System.out.println("    unravl.sh -q hello.json");
        System.out.println("");
        System.out.println("Options:");
        System.out.println("   -q | --quiet : decrease the logging level.");
        System.out.println("   -v | --verbose : increase the logging level.");
        System.out.println("   -h | --help : Display this message and exit.");
        System.out.println("   --stdout : In interactive mode, write output to the standard output, not the Output panel.");
        System.out.println("");
        System.out.println("If you do not specify any <script-file> options, start UnRAVL in");
        System.out.println("interactive mode, from which you can edit and execute scripts.");
        System.out.println("This requires a window system.");
        System.out.println("");
        System.out.println("See http://www.github.com/sassoftware/unravl");
        System.exit(1);
        
    }

    // Manage stdout/stderr which UnRAVLFrame can redirect to a UI text component
    // such that we can route Log4j console output to the text component
    private static RedirectedOutputStream out;
    private static RedirectedOutputStream err;

    /**
     * An OutputStream that proxies to a PrintStream, allowing dynamic redirection
     */
    public static class RedirectedOutputStream extends OutputStream {

        private PrintStream original;
        public void redirect(PrintStream ps) {
            original = ps;
        }
        public RedirectedOutputStream(PrintStream original) {
            this.original = original;
        }

        @Override
        public void write(int byt) throws IOException {
            original.write(byt);
        }
    }
    
    private static void rerouteStdoutStderr() {
        out = new RedirectedOutputStream(System.out);
        System.setOut(new PrintStream(out));
        err = new RedirectedOutputStream(System.err);
        System.setErr(new PrintStream(err));
    }

    public static void setOut(PrintStream os) {
        out.redirect(os);
    }

    public static void setErr(PrintStream os) {
        err.redirect(os);
    }

    private static void configureLog4j() {
        if (System.getProperty("log4j.configuration") == null) {
            try {
                Properties properties = new Properties();
                properties.load(Main.class
                        .getResourceAsStream("/log4j.properties"));
                org.apache.log4j.PropertyConfigurator.configure(properties);
            } catch (IOException e) {
                System.err.println("Could not load log4j config");
                System.exit(1);
            }
        } else {
            org.apache.log4j.BasicConfigurator.configure();
        }
    }

    public int run(String argv[]) {
        UnRAVLRuntime runtime = new UnRAVLRuntime();
        try {
            return runtime.execute(argv).report();
        } catch (UnRAVLException e) {
            int rc = runtime.report();
            return rc != 0 ? rc : 1;
        } catch (Throwable t) {
            System.err.println(t.getMessage());
            int rc = runtime.report();
            t.printStackTrace(System.err);
            return rc != 0 ? rc : 1;
        }
    }

}
