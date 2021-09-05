/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

import java.awt.Color;
import java.lang.Math;

public class Interpolate
{
	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		if (args.length == 0) {
			usage();
			return;
		}
		
		int count = Integer.parseInt(args[0]);
		String[] gradientTypes = args[1].split(",");

		for (String gradientTypeStr : gradientTypes) {

			GradientType gradientType = GradientType.valueOf(gradientTypeStr);
			System.out.println(gradientType.toString());
			
			Color from = colorFromCsv(args[2]);

			for (int i = 3; i < args.length; ++i) {

				Color to = colorFromCsv(args[i]);
				Color[] gradient = interpolateColors(from, to, count, gradientType);
				printColors(gradient);

				from = to;
			}
		}
	}

	private static void usage() {
		System.out.println("usage: java Interpolate STEPS ALGORITHMS RGB1 RGB2...");
		System.out.println("       STEPS = # of elements along each gradient");
		System.out.println("       ALGORITHMS = csv of HSB_ANGULAR, HSB_LINEAR, RGB");
		System.out.println("       RGBx = R,G,B csv e.g., 255,0,0 for red");
		System.out.println("              > 2 RGB values may be provided");
	}

	private static Color colorFromCsv(String csv) {

		String[] rgb = csv.split(",");

		return(new Color(Integer.parseInt(rgb[0]),
						 Integer.parseInt(rgb[1]),
						 Integer.parseInt(rgb[2])));
	}
	
	private static void printColors(Color[] colors) {
		for (Color c : colors) {
			String str = String.format("\t%s\t%s\t%s", c.getRed(),
									   c.getGreen(), c.getBlue());
			System.out.println(str);
		}
	}

	// +--------+
	// | Colors |
	// +--------+

	public static enum GradientType
	{
		HSB_ANGULAR,
		HSB_LINEAR,
		RGB
	}

	public static Color[] interpolateColors(Color from, Color to, int count) {
		return(interpolateColors(from, to, count, GradientType.HSB_ANGULAR));
	}

	public static Color[] interpolateColors(Color from, Color to, int count,
											GradientType gradientType) {

		// need to at least include start and end
		if (count < 2) count = 2;

		Color[] vals = new Color[count];

		float fraction = 0F;
		float gap = 1F / ((float) count - 1F);
		
		for (int i = 0; i < count; ++i) {
			vals[i] = interpolateColor(from, to, fraction, gradientType);
			fraction += gap;
		}

		// this cheats a little in case there was floating point drift along the way
		vals[count - 1] = to;
			 
		return(vals);
	}
	
	public static Color interpolateColor(Color from, Color to, float fraction) {
		return(interpolateColor(from, to, fraction, GradientType.HSB_ANGULAR));
	}
	
	public static Color interpolateColor(Color from, Color to, float fraction,
										 GradientType gradientType) {

		Color color = null;
		
		switch (gradientType) {

		    case HSB_ANGULAR:
				color = interpolateColorHSB(from, to, fraction, true);
				break;

		    case HSB_LINEAR:
				color = interpolateColorHSB(from, to, fraction, false);
				break;

		    case RGB:
				color = new Color(interpolateInt(from.getRed(), to.getRed(), fraction),
								  interpolateInt(from.getGreen(), to.getGreen(), fraction),
								  interpolateInt(from.getBlue(), to.getBlue(), fraction));
		}

		return(color);
	}

	private static Color interpolateColorHSB(Color from, Color to,
											 float fraction, boolean angular) {

		float[] fromHSB = Color.RGBtoHSB(from.getRed(), from.getGreen(), from.getBlue(), null);
		float[] toHSB = Color.RGBtoHSB(to.getRed(), to.getGreen(), to.getBlue(), null);

		float h = (angular
				   ? interpolateAngle(fromHSB[0], toHSB[0], fraction)
				   : interpolateFloat(fromHSB[0], toHSB[0], fraction));
		
		float s = interpolateFloat(fromHSB[1], toHSB[1], fraction);
		float b = interpolateFloat(fromHSB[2], toHSB[2], fraction);

		return(Color.getHSBColor(h, s, b));
	}

	// +-------+
	// | Lists |
	// +-------+

	// return an interpolated list from start to end over count steps

	public static float[] interpolateFloats(float start, float end, int count) {
		return(interpolateFloatsInternal(start, end, count, false));
	}

	public static float[] interpolateAngles(float start, float end, int count) {
		return(interpolateFloatsInternal(start, end, count, true));
	}

	private static float[] interpolateFloatsInternal(float start, float end,
													 int count, boolean angular) {

		// need to at least include start and end
		if (count < 2) count = 2;

		float[] vals = new float[count];

		float fraction = 0F;
		float gap = 1F / ((float) count - 1F);
		
		for (int i = 0; i < count; ++i) {
			
			vals[i] = (angular
					   ? interpolateAngle(start, end, fraction)
					   : interpolateFloat(start, end, fraction));
			
			fraction += gap;
		}

		// this cheats a little in case there was floating point drift along the way
		vals[count - 1] = end;
			 
		return(vals);
	}

	public static int[] interpolateInts(int start, int end, int count) {

		float[] floatVals = interpolateFloats((float) start, (float) end, count);
		
		int[] vals = new int[floatVals.length];
		for (int i = 0; i < vals.length; ++i) {
			vals[i] = (int) Math.round(floatVals[i]);
		}

		return(vals);
	}

	// +---------------+
	// | Single Values |
	// +---------------+

	// linear interpolation between two numbers at fraction 0.0 - 1.0

	public static float interpolateFloat(float start, float end, float fraction) {
		return(start + ((end - start) * fraction));
	}

	public static int interpolateInt(int start, int end, float fraction) {
		return((int) Math.round(interpolateFloat((float)start, (float)end, fraction)));
	}

	// shortest-path interpolation between two angles at fraction 0.0 - 1.0
	
	public static float interpolateAngle(float start, float end, float fraction) {

		float distClockwise = (end >= start ? end - start : 1F - (start - end));
		float distCounter = (start >= end ? start - end : 1F - (end - start));

		// this sets the distance and direction we will travel
		float dist = (distClockwise <= distCounter ? distClockwise : -1F * distCounter);

		return(start + (dist * fraction));
	}
}

