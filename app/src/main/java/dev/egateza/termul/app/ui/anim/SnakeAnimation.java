package dev.egateza.termul.app.ui.anim;

import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.SplittableRandom;

/** Ular di grid sempit mengejar makanan secara greedy, memanjang, lalu mulai lagi saat buntu atau cukup panjang. */
final class SnakeAnimation implements Animation {

    record Cell(int x, int y) {
    }

    private static final Cell[] DIRS = {new Cell(1, 0), new Cell(-1, 0), new Cell(0, 1), new Cell(0, -1)};
    static final int MAX_LENGTH = 22;

    private final int cell;
    private final int cols;
    private final int rows;
    private final int every;
    private final SplittableRandom random = new SplittableRandom(13);
    private final Deque<Cell> body = new ArrayDeque<>();
    private Cell dir;
    private Cell food;
    private int length;
    private int frame;

    SnakeAnimation(Size size) {
        boolean large = size.large();
        cell = large ? 6 : 4;
        cols = size.width() / cell;
        rows = size.height() / cell;
        every = large ? 3 : 4;
        reset();
    }

    private void reset() {
        body.clear();
        body.add(new Cell(2, rows / 2));
        dir = DIRS[0];
        length = 4;
        placeFood();
    }

    private void placeFood() {
        do {
            food = new Cell(random.nextInt(cols), random.nextInt(rows));
        } while (body.contains(food));
    }

    int length() {
        return body.size();
    }

    boolean inGrid(Cell c) {
        return c.x() >= 0 && c.y() >= 0 && c.x() < cols && c.y() < rows;
    }

    Iterable<Cell> body() {
        return body;
    }

    @Override
    public void step() {
        if (++frame % every != 0) {
            return;
        }
        Cell head = body.peekFirst();
        Cell next = null;
        Cell nextDir = null;
        int best = Integer.MAX_VALUE;
        for (Cell d : DIRS) {
            if (d.x() == -dir.x() && d.y() == -dir.y()) {
                continue;
            }
            var n = new Cell(head.x() + d.x(), head.y() + d.y());
            if (!inGrid(n) || body.contains(n)) {
                continue;
            }
            int dist = Math.abs(n.x() - food.x()) + Math.abs(n.y() - food.y());
            if (dist < best) {
                best = dist;
                next = n;
                nextDir = d;
            }
        }
        if (next == null) {
            reset(); // buntu
            return;
        }
        dir = nextDir;
        body.addFirst(next);
        if (next.equals(food)) {
            length += 2;
            if (length > MAX_LENGTH) {
                reset();
                return;
            }
            placeFood();
        }
        while (body.size() > length) {
            body.removeLast();
        }
    }

    @Override
    public void paint(Graphics2D g, int w, int h, Palette c) {
        int ox = (w - cols * cell) / 2;
        int oy = (h - rows * cell) / 2;
        g.setColor(c.amber());
        g.fillRect(ox + food.x() * cell + 1, oy + food.y() * cell + 1, cell - 2, cell - 2);
        Cell head = body.peekFirst();
        for (Cell b : body) {
            g.setColor(b == head ? c.green() : Palette.alpha(c.green(), 0.75));
            g.fillRect(ox + b.x() * cell, oy + b.y() * cell, cell - 1, cell - 1);
        }
    }
}
