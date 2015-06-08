package me.kenzierocks.tuplocity;

import java.util.Scanner;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.google.common.collect.ImmutableList;

public final class TuplocityMain {

    public static void main(String[] args) {
        OptionParser parser = new OptionParser();
        ArgumentAcceptingOptionSpec<Integer> countArgument =
                parser.acceptsAll(ImmutableList.of("c", "count"),
                                  "The number of tuples to generate")
                        .withRequiredArg().ofType(Integer.class);
        OptionSet opts = parser.parse(args);
        Integer count = countArgument.value(opts);
        if (count == null) {
            @SuppressWarnings("resource")
            Scanner s = new Scanner(System.in);
            System.out.print("Input count: ");
            count = s.nextInt();
        }
        new TupleGenerator(count).generateTupleFiles();
    }

    private TuplocityMain() {
    }

}
