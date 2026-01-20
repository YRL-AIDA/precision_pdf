package ru.sunveil.precision_pdf.pdfparser.util;

import ru.sunveil.precision_pdf.pdfparser.model.Ruling;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;

public class GeometryUtils {
    private final static float EPSILON = 0.01f;
    public final static float POINT_SNAP_DISTANCE_THRESHOLD = 8f;
    public static boolean isOverlapping(BoundingBox box1, BoundingBox box2) {
        return box1.getX() < box2.getX() + box2.getWidth() &&
                box1.getX() + box1.getWidth() > box2.getX() &&
                box1.getY() < box2.getY() + box2.getHeight() &&
                box1.getY() + box1.getHeight() > box2.getY();
    }

    public static double calculateDistance(BoundingBox box1, BoundingBox box2) {
        float centerX1 = box1.getX() + box1.getWidth() / 2;
        float centerY1 = box1.getY() + box1.getHeight() / 2;
        float centerX2 = box2.getX() + box2.getWidth() / 2;
        float centerY2 = box2.getY() + box2.getHeight() / 2;

        return Math.sqrt(Math.pow(centerX2 - centerX1, 2) + Math.pow(centerY2 - centerY1, 2));
    }

    public static boolean feq(double f1, double f2) {
        return (Math.abs(f1 - f2) < EPSILON);
    }
    public static boolean within(double first, double second, double variance) {
        return second < first + variance && second > first - variance;
    }

    public static void snapPoints(List<? extends Line2D.Float> rulings, float xThreshold, float yThreshold) {

        Map<Line2D.Float, Point2D[]> linesToPoints = new HashMap<>();
        List<Point2D> points = new ArrayList<>();
        for (Line2D.Float r : rulings) {
            Point2D p1 = r.getP1();
            Point2D p2 = r.getP2();
            linesToPoints.put(r, new Point2D[]{p1, p2});
            points.add(p1);
            points.add(p2);
        }

        Collections.sort(points, new Comparator<Point2D>() {
            @Override
            public int compare(Point2D arg0, Point2D arg1) {
                return java.lang.Double.compare(arg0.getX(), arg1.getX());
            }
        });

        List<List<Point2D>> groupedPoints = new ArrayList<>();
        groupedPoints.add(new ArrayList<>(Arrays.asList(new Point2D[]{points.get(0)})));

        for (Point2D p : points.subList(1, points.size() - 1)) {
            List<Point2D> last = groupedPoints.get(groupedPoints.size() - 1);
            if (Math.abs(p.getX() - last.get(0).getX()) < xThreshold) {
                groupedPoints.get(groupedPoints.size() - 1).add(p);
            } else {
                groupedPoints.add(new ArrayList<>(Arrays.asList(new Point2D[]{p})));
            }
        }

        for (List<Point2D> group : groupedPoints) {
            float avgLoc = 0;
            for (Point2D p : group) {
                avgLoc += p.getX();
            }
            avgLoc /= group.size();
            for (Point2D p : group) {
                p.setLocation(avgLoc, p.getY());
            }
        }

        Collections.sort(points, new Comparator<Point2D>() {
            @Override
            public int compare(Point2D arg0, Point2D arg1) {
                return java.lang.Double.compare(arg0.getY(), arg1.getY());
            }
        });

        groupedPoints = new ArrayList<>();
        groupedPoints.add(new ArrayList<>(Arrays.asList(new Point2D[]{points.get(0)})));

        for (Point2D p : points.subList(1, points.size() - 1)) {
            List<Point2D> last = groupedPoints.get(groupedPoints.size() - 1);
            if (Math.abs(p.getY() - last.get(0).getY()) < yThreshold) {
                groupedPoints.get(groupedPoints.size() - 1).add(p);
            } else {
                groupedPoints.add(new ArrayList<>(Arrays.asList(new Point2D[]{p})));
            }
        }

        for (List<Point2D> group : groupedPoints) {
            float avgLoc = 0;
            for (Point2D p : group) {
                avgLoc += p.getY();
            }
            avgLoc /= group.size();
            for (Point2D p : group) {
                p.setLocation(p.getX(), avgLoc);
            }
        }

        for (Map.Entry<Line2D.Float, Point2D[]> ltp : linesToPoints.entrySet()) {
            Point2D[] p = ltp.getValue();
            ltp.getKey().setLine(p[0], p[1]);
        }
    }

    public static List<Ruling> collapseOrientedRulings(List<Ruling> lines, int expandAmount) {
        ArrayList<Ruling> rv = new ArrayList<>();
        Collections.sort(lines, new Comparator<Ruling>() {
            @Override
            public int compare(Ruling a, Ruling b) {
                final float diff = a.getPosition() - b.getPosition();
                return java.lang.Float.compare(diff == 0 ? a.getStart() - b.getStart() : diff, 0f);
            }
        });

        for (Ruling next_line : lines) {
            Ruling last = rv.isEmpty() ? null : rv.get(rv.size() - 1);
            if (last != null && GeometryUtils.feq(next_line.getPosition(), last.getPosition()) &&
                    last.nearlyIntersects(next_line, expandAmount)) {
                final float lastStart = last.getStart();
                final float lastEnd = last.getEnd();

                final boolean lastFlipped = lastStart > lastEnd;
                final boolean nextFlipped = next_line.getStart() > next_line.getEnd();

                boolean differentDirections = nextFlipped != lastFlipped;
                float nextS = differentDirections ? next_line.getEnd() : next_line.getStart();
                float nextE = differentDirections ? next_line.getStart() : next_line.getEnd();

                final float newStart = lastFlipped ? Math.max(nextS, lastStart) : Math.min(nextS, lastStart);
                final float newEnd  = lastFlipped ? Math.min(nextE, lastEnd) : Math.max(nextE, lastEnd);
                last.setStartEnd(newStart, newEnd);
                assert !last.oblique();
            }
            else if (next_line.length() == 0) {
                continue;
            }
            else {
                rv.add(next_line);
            }
        }
        return rv;
    }
}