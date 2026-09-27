package ru.gits.taskbank;

import java.io.PrintStream;

import ru.gits.core.GitsVersion;

/**
 * Entry point of the task bank tool. Commands are implemented in prompts P05, P06 and P24.
 */
public final class TaskBankCli {

    static final int EXIT_OK = 0;
    static final int EXIT_USAGE = 64;

    private TaskBankCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    static int run(String[] args, PrintStream out) {
        if (args.length == 0 || "help".equals(args[0])) {
            printUsage(out);
            return args.length == 0 ? EXIT_USAGE : EXIT_OK;
        }
        out.println("Unknown command: " + args[0]);
        printUsage(out);
        return EXIT_USAGE;
    }

    private static void printUsage(PrintStream out) {
        out.println(GitsVersion.display() + " task bank");
        out.println("Usage: gits-taskbank <command> [options]");
        out.println("Commands (planned): validate, load, stats, demo-seed, help");
    }
}
