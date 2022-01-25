/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.evolve;

import java.lang.Math;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class MatrixTest
{
    @Test
    public void roundTripsWithScalars() throws Exception
    {
		double[] input = { 0.0, -1.0, 15.3 };
		double[] added = { 0.5, -0.5, 15.8 };
		double[] scaled = { 0.0, 2.0, -30.6 };
		double add = 0.5;
		double scale = -2.0;
		
		Matrix mTest = new Matrix(input);

		Matrix m = new Matrix(mTest);
		m.add(add);
		assertTrue(m.equals(new Matrix(added)));
		
		m.subtract(add);
		assertTrue(m.equals(mTest));

		m.scale(scale);
		assertTrue(m.equals(new Matrix(scaled)));

		m.scale(1.0 / scale);
		assertTrue(m.equals(mTest));

		m.transpose();
		double[] output = mTest.toArray();
		
		for (int i = 0; i < output.length; ++i) {
			assertTrue(deq(output[i], input[i]));
		}
    }
	
    @Test
    public void roundTripsWithMatrices() throws Exception
    {
		double[] input = { 0.0, -1.0, 15.3 };
		
		double[] add = { 0.5, 0.4, 0.3 };
		double[] added = { 0.5, -0.6, 15.6 };
		
		double[] scale = { -2.0, 4.0, 10.0 };
		double[] scaled = { 0.0, -4.0, 153.0 };
		
		Matrix mTest = new Matrix(input);

		Matrix m = new Matrix(mTest);
		m.add(new Matrix(add));
		assertTrue(m.equals(new Matrix(added)));
		
		m.subtract(new Matrix(add));
		assertTrue(m.equals(mTest));

		Matrix s = new Matrix(scale);
		
		m.scale(s);
		assertTrue(m.equals(new Matrix(scaled)));

		s.iterate((r,c) -> { s.putCell(r, c, 1.0 / s.getCell(r, c)); } );
		m.scale(s);
		assertTrue(m.equals(mTest));

		m.transpose();
		double[] output = mTest.toArray();
		
		for (int i = 0; i < output.length; ++i) {
			assertTrue(deq(output[i], input[i]));
		}
    }

    @Test
    public void transpose() throws Exception
    {
		double[][] input = { { 1.0, 2.0, 3.0 },
							 { 6.0, 5.0, 4.0 } };

		double[][] trans = { { 1.0, 6.0 },
							 { 2.0, 5.0 },
							 { 3.0, 4.0 } };

		assertTrue(new Matrix(input).transpose().equals(new Matrix(trans)));
		assertTrue(new Matrix(trans).transpose().equals(new Matrix(input)));
	}

    @Test
    public void matrixMultiply() throws Exception
    {
		double[][] a = { { 1d, 2d, 3d },
						 { 6d, 5d, 4d } };

		double[][] b = { { 1d, 6d },
						 { 2d, 5d },
						 { 3d, 4d } };

		double[][] m = { { 1d*1d + 2d*2d + 3d*3d, 1d*6d + 2d*5d + 3d*4d },
						 { 6d*1d + 5d*2d + 4d*3d, 6d*6d + 5d*5d + 4d*4d } };

		assertTrue(new Matrix(a).multiply(new Matrix(b)).equals(new Matrix(m)));
	}

    @Test
    public void randomize() throws Exception {
		double min = -2.0;
		double max = 3.0;

		Matrix m = new Matrix(10, 8);
		m.randomize(min, max);

		m.iterate((r,c) -> {
			double d = m.getCell(r,c);
			assertTrue(d >= min && d <= max);
		});
	}

	private static boolean deq(double d1, double d2) {
		return(Math.abs(d1 - d2) < 0.000001);
	}
}
