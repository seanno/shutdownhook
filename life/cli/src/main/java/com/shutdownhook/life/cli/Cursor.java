//
// CURSOR.JAVA
//

package com.shutdownhook.life.cli;

public class Cursor
{
	public static void set(int x, int y) {
		System.out.print(String.format("%s%d;%df", ESC, x, y));
	}

	public static void cls() {
		System.out.print(String.format("%s2J", ESC));
		Cursor.set(0,0);
	}

	private static String ESC = "\u001b[";
}
