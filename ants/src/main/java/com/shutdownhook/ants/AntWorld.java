
// ANTWORLD.JAVA
//

package com.shutdownhook.ants;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

public class AntWorld
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public int WorldX = 25;
		public int WorldY = 25;
		public int AntCount = 50;

		public int AntReturningPheremone = 25;
		public int AntExplorationLikelihoodPct = 20;
		
		public int LocationPheremoneMax = 200;
		public int LocationPheremoneDecay = 1;

		public int FoodCaches = 2;
		public int FoodCacheSize = 50;

		public String ImageFormat = "jpeg";
		public int ImageCellDp = 20;
		public String ImageMaxPheremoneRGB = "0x00FF00";
		public String ImageMaxFoodRGB = "0x0000FF";
		public String ImageColonyRGB = "0xFF0000";
		public String ImageAntsRGB = "0x202020";

		public Long Seed = null;
	}

	public AntWorld(Config cfg) {
		
		this.cfg = cfg;

		if (cfg.Seed == null) cfg.Seed = System.nanoTime();
		this.rand = new Random(cfg.Seed);

		cacheColors();
		
		initializeWorld();
	}

	// +------------+
	// | Model: Ant |
	// +------------+

	public enum AntMode
	{
		Waiting,
		Exploring,
		ReturningWithFood,
		ReturningEmpty
	}

	public static enum Direction {

		North(0,-1),
		NorthEast(1,-1),
		East(1, 0),
		SouthEast(1,1),
		South(0,1),
		SouthWest(-1,1),
		West(-1,0),
		NorthWest(-1,-1);

		private Direction(int dx, int dy) {
			this.Dx = dx;
			this.Dy = dy;
		}

		public Direction counter() {
			int i = this.ordinal();
			return(Direction.of(i == 0 ? 7: i - 1));
		}

		public Direction clock() {
			int i = this.ordinal();
			return(Direction.of(i == 7 ? 0: i + 1));
		}

		public Direction flip() {
			int i = this.ordinal();
			return(Direction.of(i >= 4 ? i - 4 : i + 4));
		}

		public static Direction of(int i) {
			return(Direction.values()[i]);
		}
		
		public static Direction random(Random rand) {
			return(Direction.values()[rand.nextInt(8)]);
		}

		public int Dx;
		public int Dy;
	}
	
	public static class Ant
	{
		public int X;
		public int Y;
		public AntMode Mode;
		public Direction LastDirection;
	}

	// +-----------------+
	// | Model: Location |
	// +-----------------+

	public static class Location
	{
		public int Ants = 0;
		public int Food = 0;
		public boolean Colony = false;
		public int Pheremone = 0;
	}

	// +-----------------+
	// | initializeWorld |
	// +-----------------+

	private void initializeWorld() {

		// 1. Create the world
		
		this.world = new Location[cfg.WorldX][cfg.WorldY];

		// 2. put ants in the colony

		this.colonyX = cfg.WorldX / 2;
		this.colonyY = cfg.WorldY / 2;
		getLocation(colonyX, colonyY).Colony = true;

		this.ants = new Ant[cfg.AntCount];
		for (int i = 0; i < cfg.AntCount; ++i) {
			
			Ant ant = new Ant();
			ant.X = colonyX;
			ant.Y = colonyY;
			ant.Mode = AntMode.Waiting;
			ant.LastDirection = null;
			
			this.ants[i] = ant;
			getLocation(ant.X, ant.Y).Ants++;
		}

		// 3. set up food caches

		for (int i = 0; i < cfg.FoodCaches; ++i) {

			// find a place to put food that isn't on the colony or already food
			// NOTE: this could loop forever iff you're super dumb

			Location foodLoc = null;
			
			do { foodLoc = getLocation(randomX(), randomY()); }
			while (foodLoc.Colony || (foodLoc.Food > 0));

			foodLoc.Food = cfg.FoodCacheSize;
		}
	}
	
	// +-------+
	// | cycle |
	// +-------+

	public void cycle() {
		moveAnts();
		decayPheremone();
		dropPheremone();
	}

	private void decayPheremone() {
		for (int x = 0; x < cfg.WorldX; ++x) {
			for (int y = 0; y < cfg.WorldY; ++y) {
				Location loc = getLocation(x, y, false);
				if (loc != null) {
					loc.Pheremone -= cfg.LocationPheremoneDecay;
					if (loc.Pheremone < 0) loc.Pheremone = 0;
				}
			}
		}
	}

	private void dropPheremone() {
		for (int i = 0; i < ants.length; ++i) {
			if (ants[i].Mode == AntMode.ReturningWithFood) {
				Location loc = getLocation(ants[i].X, ants[i].Y);
				loc.Pheremone += cfg.AntReturningPheremone;
				if (loc.Pheremone > cfg.LocationPheremoneMax) {
					loc.Pheremone = cfg.LocationPheremoneMax;
				}
			}
		}
	}
	
	// +----------+
	// | moveAnts |
	// +----------+

	private void moveAnts() {
		for (int i = 0; i < ants.length; ++i) moveAnt(ants[i], i);
	}

	private void moveAnt(Ant ant, int i) {

		Location currentLoc = getLocation(ant.X, ant.Y);

		// WAITING: see if we're ready for action!
		// this means there is pheremone to be had OR we randomly decide to look
		if (ant.Mode == AntMode.Waiting) {

			Direction mostPheremone = directionToMostPheremone(ant);
			if (pheremoneAt(ant, mostPheremone) > 0 ||
				cfg.AntExplorationLikelihoodPct >= (rand.nextInt(100) + 1)) {

				ant.Mode = AntMode.Exploring;
				ant.LastDirection = mostPheremone;
			}
			
			return;
		}

		// RETURNING: try to get back to the colony (dead reckoning)
		if (ant.Mode == AntMode.ReturningWithFood ||
			ant.Mode == AntMode.ReturningEmpty) {
			
			ant.LastDirection = directionToColony(ant);
			ant.X += ant.LastDirection.Dx;
			ant.Y += ant.LastDirection.Dy;

			currentLoc.Ants--;
			Location newLoc = getLocation(ant.X, ant.Y);
			newLoc.Ants++;

			if (newLoc.Colony) {
				ant.Mode = AntMode.Waiting;
				ant.LastDirection = null;
			}

			return;
		}

		// EXPLORING: move forward a step, preferring locations with more pheremone
		// special case if we're right next to food go there because obviously
		// we can smell it's there!

		Direction nextDirection = directionToFood(ant);

		if (nextDirection == null) {

			int[] weights = new int[3];
			weights[0] = 1 + pheremoneAt(ant, ant.LastDirection);
			weights[1] = weights[0] + 1 + pheremoneAt(ant, ant.LastDirection.counter());
			weights[2] = weights[1] + 1 + pheremoneAt(ant, ant.LastDirection.clock());

			int rando = rand.nextInt(weights[2]);
		
			if (rando < weights[0]) { nextDirection = ant.LastDirection; }
			else if (rando < weights[1]) { nextDirection = ant.LastDirection.counter(); }
			else { nextDirection = ant.LastDirection.clock(); }
		}

		int newX = ant.X + nextDirection.Dx;
		int newY = ant.Y + nextDirection.Dy;

		if (offWorld(newX, newY)) {
			// failed to find food after hitting the edge; go home and try again
			ant.Mode = AntMode.ReturningEmpty;
			return;
		}
		
		ant.X = newX;
		ant.Y = newY;
		ant.LastDirection = nextDirection;

		currentLoc.Ants--;
		Location newLoc = getLocation(ant.X, ant.Y);
		newLoc.Ants++;
		
		if (newLoc.Food > 0) {
			newLoc.Food--;
			ant.Mode = AntMode.ReturningWithFood;
			ant.LastDirection = ant.LastDirection.flip();
		}
	}

	// +--------------------------+
	// | pheremoneAt              |
	// | directionToMostPheremone |
	// +--------------------------+

	private int pheremoneAt(Ant ant, Direction direction) {
		Location loc = getLocationAt(ant, direction);
		return(loc == null ? 0 : loc.Pheremone);
	}

	private Direction directionToMostPheremone(Ant ant) {
		
		// this is a cheap and lazy way of making sure if all are zero,
		// we get a random direction rather than the same one every time
		Direction dirMax = Direction.random(rand);
		int pheremoneMax = pheremoneAt(ant, dirMax);

		for (int i = 0; i < Direction.values().length; ++i) {
			int pheremoneHere = pheremoneAt(ant, Direction.of(i));
			if (pheremoneHere > pheremoneMax) {
				dirMax = Direction.of(i);
				pheremoneMax = pheremoneHere;
			}
		}

		return(dirMax);
	}

	// +-----------------+
	// | foodAt           |
	// | directionToFood |
	// +-----------------+

	private int foodAt(Ant ant, Direction direction) {
		Location loc = getLocationAt(ant, direction);
		return(loc == null ? 0 : loc.Food);
	}

	private Direction directionToFood(Ant ant) {

		// this is a (very) lazy way of picking a random cache if there
		// are multiples nearby. If perf ever matters, look here! ;)
		Direction[] values = Direction.values();
		shuffle(values); 
		
		for (int i = 0; i < Direction.values().length; ++i) {
			int food = foodAt(ant, Direction.of(i));
			if (food > 0) return(Direction.of(i));
		}

		return(null);
	}

	// +-------------------+
	// | directionToColony |
	// +-------------------+

	private Direction directionToColony(Ant ant) {

		if (colonyX < ant.X) {
			if (colonyY < ant.Y) return(Direction.NorthWest);
			if (colonyY > ant.Y) return(Direction.SouthWest);
			return(Direction.West);
		}
		
		if (colonyX > ant.X) {
			if (colonyY < ant.Y) return(Direction.NorthEast);
			if (colonyY > ant.Y) return(Direction.SouthEast);
			return(Direction.East);
		}
		
		if (colonyY < ant.Y) return(Direction.North);
		if (colonyY > ant.Y) return(Direction.South);

		return(null);
	}

	// +---------------------+
	// | renderDataURL       |
	// | renderBufferedImage |
	// +---------------------+

	public String renderDataURL() throws Exception {
		
		BufferedImage img = renderBufferedImage();

		// close is a documented nop for BAOS so don't worry about it
		ByteArrayOutputStream stm = new ByteArrayOutputStream();
		boolean ret = ImageIO.write(img, cfg.ImageFormat, stm);
		if (!ret) throw new Exception("ImageIO write failed");

		String b64 = Base64
			.getEncoder()
			.encodeToString(stm.toByteArray());

		return("data:image/" + cfg.ImageFormat + ";base64," + b64);
	}

	public BufferedImage renderBufferedImage() throws Exception {

		BufferedImage img = new BufferedImage(cfg.WorldX * cfg.ImageCellDp,
											  cfg.WorldY * cfg.ImageCellDp,
											  BufferedImage.TYPE_INT_RGB);

		for (int x = 0; x < cfg.WorldX; ++x) {
			for (int y = 0; y < cfg.WorldY; ++y) {

				int rgb = 0;
				Location loc = getLocation(x, y, false);
				
				if (loc != null && loc.Colony) {
					rgb = colonyRGB;
				}
				else if (loc != null && (loc.Food > 0)) {
					rgb = foodRGBs[loc.Food];
				}
				else {
					rgb = pheremoneRGBs[loc == null ? 0 : loc.Pheremone];
				}

				fillRect(img, x * cfg.ImageCellDp, y * cfg.ImageCellDp,
						 cfg.ImageCellDp, rgb);

				if (loc != null && loc.Ants > 0) {

					int antDp = loc.Ants * 2;
					if (antDp > (cfg.ImageCellDp - 2)) antDp = cfg.ImageCellDp - 2;

					int offset = (cfg.ImageCellDp / 2) - (antDp / 2);
					int antX = (x * cfg.ImageCellDp) + offset;
					int antY = (y * cfg.ImageCellDp) + offset;
					
					fillRect(img, antX, antY, antDp, antsRGB);
				}
			}
		}

		return(img);
	}

	private static void fillRect(BufferedImage img, int x, int y, int dp, int rgb) {
		for (int xRect = x; xRect < x + dp; ++xRect) {
			for (int yRect = y; yRect < y + dp; ++yRect) {
				img.setRGB(xRect, yRect, rgb);
			}
		}
	}

	// +------------------+
	// | getPheremoneRGBs |
	// +------------------+

	private final static int HUE = 0;
	private final static int SAT = 1;
	private final static int BRT = 2;

	private final static float MIN_SAT = 0.05f;
	private final static float MAX_SAT = 1.0f;

	private void cacheColors() {

		this.maxPheremoneRGB = parseHexRGB(cfg.ImageMaxPheremoneRGB);
		this.maxFoodRGB = parseHexRGB(cfg.ImageMaxFoodRGB);
		this.colonyRGB = parseHexRGB(cfg.ImageColonyRGB);
		this.antsRGB = parseHexRGB(cfg.ImageAntsRGB);

		this.pheremoneRGBs = getSaturationArray(maxPheremoneRGB, cfg.LocationPheremoneMax);
		this.foodRGBs = getSaturationArray(maxFoodRGB, cfg.FoodCacheSize);
	}

	private static int parseHexRGB(String s) {
		String clean = (s.startsWith("0x") ? s.substring(2) : s);
		return(Integer.parseInt(clean, 16));
	}

	private static int[] getSaturationArray(int maxRGBInt, int maxCount) {
		
		Color maxRGB = new Color(maxRGBInt);
		
		float[] maxHSB = Color.RGBtoHSB(maxRGB.getRed(),
										maxRGB.getGreen(),
										maxRGB.getBlue(),
										null);

		int[] rgbs = new int[maxCount + 1];
		rgbs[0] = 0xFFFFFF;

		for (int i = 1; i <= maxCount; ++i) {

			float sat =
				((((float)i) / ((float)maxCount)) *
				 (MAX_SAT - MIN_SAT)) + MIN_SAT;
				
			rgbs[i] = Color.HSBtoRGB(maxHSB[HUE], sat, maxHSB[BRT]);
		}

		return(rgbs);
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private int randomX() { return(rand.nextInt(cfg.WorldX)); }
	private int randomY() { return(rand.nextInt(cfg.WorldY)); }

	private boolean offWorld(int x, int y) {
		return(x < 0 || x >= cfg.WorldX || y < 0 || y >= cfg.WorldY);
	}

	private Location getLocation(int x, int y) {
		return(getLocation(x, y, true));
	}
	
	private Location getLocation(int x, int y, boolean createIfNeeded) {

		if (offWorld(x, y)) return(null);
		
		Location loc = world[x][y];

		if (loc == null && createIfNeeded) {
			loc = new Location();
			world[x][y] = loc;
		}

		return(loc);
	}

	private Location getLocationAt(Ant ant, Direction dir) {
		// this one might be null (empty or offWorld)
		return(getLocation(ant.X + dir.Dx, ant.Y + dir.Dy, false));
	}
	
	private void shuffle(Direction[] dirs) {
		for (int i = 0; i < dirs.length; ++i) {
			int iSwap = rand.nextInt(dirs.length);
			Direction temp = dirs[iSwap];
			dirs[iSwap] = dirs[i];
			dirs[i] = temp;
		}
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private Random rand;

	private Ant[] ants;
	private Location[][] world;
	private int colonyX;
	private int colonyY;

	private int[] pheremoneRGBs;
	private int maxPheremoneRGB;
	private int[] foodRGBs;
	private int maxFoodRGB;
	private int colonyRGB;
	private int antsRGB;
	
	private final static Logger log = Logger.getLogger(AntWorld.class.getName());
}
