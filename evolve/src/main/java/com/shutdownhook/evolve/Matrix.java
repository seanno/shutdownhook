/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.evolve;

import java.lang.IllegalArgumentException;
import java.lang.Math;
import java.lang.StringBuilder;
import java.util.ArrayList;
import java.util.List;

public class Matrix
{
	// +-------+
	// | Setup |
	// +-------+

	public Matrix(int rows, int cols) throws IllegalArgumentException {

		if (rows < 1 || cols < 1) {
			throw new IllegalArgumentException();
		}

		cells = new double[rows][cols];
		
		this.rows = rows;
		this.cols = cols;
	}

	public Matrix(Matrix m) throws IllegalArgumentException {
		this(m.rows, m.cols);
		iterate((r,c) -> { cells[r][c] = m.cells[r][c]; });
	}

	public Matrix(double[][] rgrgd) throws IllegalArgumentException {
		this(rgrgd.length, rgrgd[0].length);
		iterate((r,c) -> { cells[r][c] = rgrgd[r][c]; });
	}

	public Matrix(double[] rgd) throws IllegalArgumentException {
		this(rgd, 0, rgd.length);
	}

	public Matrix(double[] rgd, int ifirst, int imac) throws IllegalArgumentException {
		this(imac - ifirst, 1);
		iterate((r,c) -> { cells[r][c] = rgd[r+ifirst]; });
	}

	public int getRows() { return(rows); }
	public int getCols() { return(cols); }

	public double getCell(int row, int col) { return(cells[row][col]); }
	public void putCell(int row, int col, double val) { cells[row][col] = val; }

	// +-----------+
	// | randomize |
	// +-----------+

	public void randomize(double min, double max) {
		transform(i -> ((Math.random() * (max - min)) + min));
	}

	public void randomize() {
		randomize(-1d, 1d);
	}

	// +-----------+
	// | transpose |
	// +-----------+

	public Matrix transpose() {
		Matrix m = new Matrix(cols, rows);
		iterate((r,c) -> { m.cells[c][r] = cells[r][c]; });
		return(m);
	}

	// +----------+
	// | multiply |
	// +----------+

	public Matrix multiply(Matrix b) throws IllegalArgumentException {

		if (cols != b.rows) throw new IllegalArgumentException();
		
		Matrix m = new Matrix(rows, b.cols);
		
		m.iterate((rowM,colM) -> {
			// dot produt of rowM of this with colM of b
			m.cells[rowM][colM] = 0.0;
			for (int i = 0; i < cols; ++i) {
				m.cells[rowM][colM] += (cells[rowM][i] * b.cells[i][colM]);
			}
		});

		return(m);
	}

	// +-------+
	// | scale |
	// +-------+

	public void scale(double d) { transform(i -> i * d); }

	public void scale(Matrix m) throws IllegalArgumentException {
		if (rows != m.rows || cols != m.cols) {
			throw new IllegalArgumentException("matrix size mismatch");
		}

		iterate((r,c) -> { cells[r][c] *= m.cells[r][c]; });
	}

	// +----------------+
	// | add / subtract |
	// +----------------+

	public void add(double d) { transform(i -> i + d); }
	public void subtract(double d) { transform(i -> i - d); }

	public void add(Matrix m) throws IllegalArgumentException {
		if (rows != m.rows || cols != m.cols) {
			throw new IllegalArgumentException("matrix size mismatch");
		}
		
		iterate((r,c) -> { cells[r][c] += m.cells[r][c]; });
	}
	
	public void subtract(Matrix m) throws IllegalArgumentException {
		if (rows != m.rows || cols != m.cols) {
			throw new IllegalArgumentException("matrix size mismatch");
		}
		
		iterate((r,c) -> { cells[r][c] -= m.cells[r][c]; });
	}
	
	// +-----------------+
	// | Traversal Sugar |
	// +-----------------+

	public interface MatrixIterator {
		public void cell(int row, int col);
	}

	public void iterate(MatrixIterator iter) {
		for (int r = 0; r < rows; ++r) {
			for (int c = 0; c < cols; ++c) {
				iter.cell(r, c);
			}
		}
	}

	public interface MatrixOperator {
		public double op(double input);
	}

	public void transform(MatrixOperator op) {
		iterate((r,c) -> { cells[r][c] = op.op(cells[r][c]); });

	}

	// +--------+
	// | equals |
	// +--------+

	public static final double DEFAULT_EPSILON = 0.000001d;
	
	@Override
	public boolean equals(Object o) {
		
		if (o == this) return(true);
		if (!(o instanceof Matrix)) return(false);

		return(equals((Matrix)o, DEFAULT_EPSILON));
	}

	public boolean equals(Matrix m, double epsilon) {
		
		if (rows != m.rows || cols != m.cols) {
			return(false);
		}

		for (int r = 0; r < rows; ++r) {
			for (int c = 0; c < cols; ++c) {
				double cmp = Math.abs(cells[r][c] - m.cells[r][c]);
				if (cmp > epsilon) return(false);
			}
		}

		return(true);
	}

	// +-------------+
	// | Conversions |
	// +-------------+

	@Override
	public String toString() {
		
		StringBuilder sb = new StringBuilder();
		
		iterate((r,c) -> {
			sb.append(String.format("%s%s%04.3f",
									c > 0 && r == 0 ? "\n" : "",
									r > 0 ? "\t" : "",
									cells[r][c]));
		});

		return(sb.toString());
	}

	public double[] toArray() throws IllegalArgumentException {

		if (rows == 1) {

			// easy!
			return(cells[0]);
		}
		else if (cols == 1) {
			
			// gotta copy
			double[] rgd = new double[rows];
			for (int r = 0; r < rows; ++r) rgd[r] = cells[r][0];
			return(rgd);
		}
		
		// oops
		throw new IllegalArgumentException();
	}

	// +---------+
	// | Members |
	// +---------+

	private double[][] cells;
	int rows;
	int cols;
}
