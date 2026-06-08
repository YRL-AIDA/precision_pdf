package ru.sunveil.precision_pdf.benchmark;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TextAccuracyMetrics {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TextAccuracyMetrics() {
    }

    public record Result(
            double accuracy,
            double editSimilarity,
            double characterErrorRate,
            int normalizedGroundTruthChars,
            int normalizedPredictionChars
    ) {
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return WHITESPACE.matcher(text.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    public static Result compute(String groundTruth, String prediction) {
        String gt = normalize(groundTruth);
        String pred = normalize(prediction);

        if (gt.isEmpty() && pred.isEmpty()) {
            return new Result(1.0, 1.0, 0.0, 0, 0);
        }
        if (gt.isEmpty() || pred.isEmpty()) {
            return new Result(0.0, 0.0, 1.0, gt.length(), pred.length());
        }

        int distance = levenshteinDistance(gt, pred);
        int maxLen = Math.max(gt.length(), pred.length());
        double editSimilarity = 1.0 - ((double) distance / maxLen);
        double cer = (double) distance / gt.length();
        return new Result(editSimilarity, editSimilarity, cer, gt.length(), pred.length());
    }

    private static int levenshteinDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                int insertCost = curr[j - 1] + 1;
                int deleteCost = prev[j] + 1;
                int replaceCost = prev[j - 1] + (ca == b.charAt(j - 1) ? 0 : 1);
                curr[j] = Math.min(insertCost, Math.min(deleteCost, replaceCost));
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }
}
