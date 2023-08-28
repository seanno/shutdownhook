/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.toolbox;

public class Convert
{
	public static final double celsiusToFarenheit(double c) {
		return((c * 9.0 / 5.0) + 32.0);
	}

	public static final double metersToMiles(double m) {
		return(m / 1609.344);
	}
	
	public static final double millimetersToInches(double mm) {
		return(mm / 25.4);
	}

	public static final double cmToFeet(double cm) {
		return(cm * 0.0328084);
	}
}
