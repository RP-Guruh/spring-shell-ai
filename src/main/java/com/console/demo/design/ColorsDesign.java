package com.console.demo.design;

public final class ColorsDesign {

    private static final String RESET = "\u001B[0m";

    private ColorsDesign() {}

    public static String gold(String text)  { return rgb(255, 190, 0, text); }
    public static String green(String text) { return rgb(80, 200, 120, text); }
    public static String red(String text)   { return rgb(230, 80, 80, text); }
    public static String bold(String text)  { return "\u001B[1m" + text + RESET; }

    private static String rgb(int r, int g, int b, String text) {
        return "\u001B[38;2;" + r + ";" + g + ";" + b + "m" + text + RESET;
    }
}