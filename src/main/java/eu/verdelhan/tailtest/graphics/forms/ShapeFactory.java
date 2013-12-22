package net.sf.tail.graphics.forms;

import java.awt.Polygon;
import java.awt.Shape;

public class ShapeFactory {

	public static Polygon getUpperArrow() {
		return new Polygon(new int[] { 0, 3, 1, 1, -1, -1, -3 }, new int[] { -4, -11, -11, -17, -17, -11, -11 }, 7);
	}

	public static Polygon getDownArrow() {
		return new Polygon(new int[] { 0, 3, 1, 1, -1, -1, -3 }, new int[] { 4, 11, 11, 17, 17, 11, 11 }, 7);
	}

	public static Shape getPoint() {
		return new Polygon(new int[] { 0, 1, 0, -1 }, new int[] { 1, 1, -1, -1 }, 4);
	}

}
