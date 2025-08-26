package ru.sunveil.precision_pdf.pdfparser.model.core;

import lombok.Data;

import java.util.Objects;

/**
 * Класс для представления ограничивающей рамки (bounding box) в PDF документе.
 * Координаты используются в PDF coordinate system (нижний левый угол - начало координат).
 */

@Data
public class BoundingBox {
    private float x;
    private float y;
    private float width;
    private float height;

    /**
     * Конструктор по умолчанию
     */
    public BoundingBox() {
        this(0, 0, 0, 0);
    }

    /**
     * Конструктор с параметрами
     *
     * @param x координата X левого нижнего угла
     * @param y координата Y левого нижнего угла
     * @param width ширина рамки
     * @param height высота рамки
     */
    public BoundingBox(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Конструктор копирования
     *
     * @param other другой объект BoundingBox для копирования
     */
    public BoundingBox(BoundingBox other) {
        this(other.x, other.y, other.width, other.height);
    }

    // Getters and Setters
    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = Math.max(0, width);
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = Math.max(0, height);
    }

    /**
     * Возвращает координату X правого верхнего угла
     */
    public float getRight() {
        return x + width;
    }

    /**
     * Возвращает координату Y правого верхнего угла
     */
    public float getTop() {
        return y + height;
    }

    /**
     * Возвращает координату X центра рамки
     */
    public float getCenterX() {
        return x + width / 2.0f;
    }

    /**
     * Возвращает координату Y центра рамки
     */
    public float getCenterY() {
        return y + height / 2.0f;
    }

    /**
     * Возвращает площадь рамки
     */
    public float getArea() {
        return width * height;
    }

    /**
     * Проверяет, содержит ли текущая рамка другую рамку полностью
     *
     * @param other другая рамка для проверки
     * @return true если текущая рамка содержит другую рамку полностью
     */
    public boolean contains(BoundingBox other) {
        if (other == null) return false;

        return this.x <= other.x &&
                this.y <= other.y &&
                this.getRight() >= other.getRight() &&
                this.getTop() >= other.getTop();
    }

    /**
     * Проверяет, содержит ли текущая рамка точку с указанными координатами
     *
     * @param pointX координата X точки
     * @param pointY координата Y точки
     * @return true если точка находится внутри рамки
     */
    public boolean contains(float pointX, float pointY) {
        return pointX >= x &&
                pointX <= getRight() &&
                pointY >= y &&
                pointY <= getTop();
    }

    /**
     * Проверяет, пересекается ли текущая рамка с другой рамкой
     *
     * @param other другая рамка для проверки
     * @return true если рамки пересекаются
     */
    public boolean intersects(BoundingBox other) {
        if (other == null) return false;

        return !(other.getRight() < x ||
                other.x > getRight() ||
                other.getTop() < y ||
                other.y > getTop());
    }

    /**
     * Вычисляет площадь пересечения с другой рамкой
     *
     * @param other другая рамка
     * @return площадь пересечения, или 0 если не пересекаются
     */
    public float intersectionArea(BoundingBox other) {
        if (!intersects(other)) return 0;

        float intersectX = Math.max(x, other.x);
        float intersectY = Math.max(y, other.y);
        float intersectRight = Math.min(getRight(), other.getRight());
        float intersectTop = Math.min(getTop(), other.getTop());

        float intersectWidth = Math.max(0, intersectRight - intersectX);
        float intersectHeight = Math.max(0, intersectTop - intersectY);

        return intersectWidth * intersectHeight;
    }

    /**
     * Вычисляет коэффициент IoU (Intersection over Union)
     *
     * @param other другая рамка
     * @return значение IoU от 0 до 1
     */
    public float intersectionOverUnion(BoundingBox other) {
        if (other == null) return 0;

        float intersectionArea = intersectionArea(other);
        float unionArea = getArea() + other.getArea() - intersectionArea;

        if (unionArea == 0) return 0;

        return intersectionArea / unionArea;
    }

    /**
     * Объединяет текущую рамку с другой рамкой
     *
     * @param other другая рамка
     * @return новая рамка, содержащая обе исходные рамки
     */
    public BoundingBox union(BoundingBox other) {
        if (other == null) return new BoundingBox(this);

        float minX = Math.min(x, other.x);
        float minY = Math.min(y, other.y);
        float maxRight = Math.max(getRight(), other.getRight());
        float maxTop = Math.max(getTop(), other.getTop());

        return new BoundingBox(minX, minY, maxRight - minX, maxTop - minY);
    }

    /**
     * Вычисляет пересечение с другой рамкой
     *
     * @param other другая рамка
     * @return новая рамка, представляющая пересечение, или null если не пересекаются
     */
    public BoundingBox intersection(BoundingBox other) {
        if (!intersects(other)) return null;

        float intersectX = Math.max(x, other.x);
        float intersectY = Math.max(y, other.y);
        float intersectRight = Math.min(getRight(), other.getRight());
        float intersectTop = Math.min(getTop(), other.getTop());

        return new BoundingBox(
                intersectX,
                intersectY,
                intersectRight - intersectX,
                intersectTop - intersectY
        );
    }

    /**
     * Масштабирует рамку на указанный коэффициент
     *
     * @param scaleX коэффициент масштабирования по X
     * @param scaleY коэффициент масштабирования по Y
     * @return новая масштабированная рамка
     */
    public BoundingBox scale(float scaleX, float scaleY) {
        return new BoundingBox(
                x * scaleX,
                y * scaleY,
                width * scaleX,
                height * scaleY
        );
    }

    /**
     * Сдвигает рамку на указанные расстояния
     *
     * @param dx смещение по X
     * @param dy смещение по Y
     * @return новая сдвинутая рамка
     */
    public BoundingBox translate(float dx, float dy) {
        return new BoundingBox(x + dx, y + dy, width, height);
    }

    /**
     * Проверяет, является ли рамка валидной (положительные размеры)
     */
    public boolean isValid() {
        return width > 0 && height > 0;
    }

    /**
     * Вычисляет расстояние между центрами двух рамок
     *
     * @param other другая рамка
     * @return расстояние между центрами
     */
    public float distanceTo(BoundingBox other) {
        if (other == null) return Float.MAX_VALUE;

        float dx = getCenterX() - other.getCenterX();
        float dy = getCenterY() - other.getCenterY();

        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Вычисляет аспектное соотношение (width/height)
     */
    public float getAspectRatio() {
        if (height == 0) return Float.MAX_VALUE;
        return width / height;
    }

    /**
     * Нормализует координаты относительно размеров страницы
     *
     * @param pageWidth ширина страницы
     * @param pageHeight высота страницы
     * @return новая нормализованная рамка
     */
    public BoundingBox normalize(float pageWidth, float pageHeight) {
        if (pageWidth <= 0 || pageHeight <= 0) {
            return new BoundingBox(this);
        }

        return new BoundingBox(
                x / pageWidth,
                y / pageHeight,
                width / pageWidth,
                height / pageHeight
        );
    }

    /**
     * Денормализует координаты относительно размеров страницы
     *
     * @param pageWidth ширина страницы
     * @param pageHeight высота страницы
     * @return новая денормализованная рамка
     */
    public BoundingBox denormalize(float pageWidth, float pageHeight) {
        return new BoundingBox(
                x * pageWidth,
                y * pageHeight,
                width * pageWidth,
                height * pageHeight
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BoundingBox that = (BoundingBox) o;
        return Float.compare(that.x, x) == 0 &&
                Float.compare(that.y, y) == 0 &&
                Float.compare(that.width, width) == 0 &&
                Float.compare(that.height, height) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height);
    }

    @Override
    public String toString() {
        return String.format("BoundingBox{x=%.2f, y=%.2f, width=%.2f, height=%.2f}",
                x, y, width, height);
    }

    /**
     * Создает копию текущего объекта
     */
    public BoundingBox copy() {
        return new BoundingBox(this);
    }

    /**
     * Статический метод для создания рамки из координат углов
     *
     * @param left левая координата
     * @param bottom нижняя координата
     * @param right правая координата
     * @param top верхняя координата
     * @return новый объект BoundingBox
     */
    public static BoundingBox fromCorners(float left, float bottom, float right, float top) {
        return new BoundingBox(left, bottom, right - left, top - bottom);
    }

    /**
     * Статический метод для создания рамки из центра и размеров
     *
     * @param centerX координата X центра
     * @param centerY координата Y центра
     * @param width ширина
     * @param height высота
     * @return новый объект BoundingBox
     */
    public static BoundingBox fromCenter(float centerX, float centerY, float width, float height) {
        return new BoundingBox(
                centerX - width / 2.0f,
                centerY - height / 2.0f,
                width,
                height
        );
    }

    /**
     * Статический метод для создания пустой невалидной рамки
     */
    public static BoundingBox empty() {
        return new BoundingBox(0, 0, 0, 0);
    }
}