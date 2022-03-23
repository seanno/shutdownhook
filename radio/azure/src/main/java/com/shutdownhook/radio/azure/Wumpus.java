/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

// Code modified from:
// https://rosettacode.org/wiki/Hunt_The_Wumpus/Java

package com.shutdownhook.radio.azure;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Wumpus
{
	// +-------------+
	// | Constructor |
	// +-------------+

	public Wumpus() {
		newGame();
	}

	public Wumpus(WumpusState state) {
		this.s = state;
		if (s.hazards == null) newGame();
	}

	private WumpusState s;

	// +-------+
	// | State |
	// +-------+

	static public Wumpus fromSerializedState(String serializedState) throws JsonProcessingException {
		WumpusState state = objectMapper.readValue(serializedState, WumpusState.class);
		return(new Wumpus(state));
	}
	
	public String serializeState() throws JsonProcessingException {
		return(objectMapper.writeValueAsString(s));
	}
	
	static public class WumpusState
	{
		public int currRoom;
		public int numArrows;

		public List<Set<Hazard>> hazards;
	}

    static public enum Hazard
	{
        Wumpus("There's an awful smell nearby..."),
        Bat("You hear a rustling sound..."),
        Pit("You feel a draft...");
 
        Hazard(String warning) {
            this.warning = warning;
        }
		
        final String warning;
    }

	// +---------+
	// | newGame |
	// +---------+

    private void newGame() {

		if (s == null) s = new WumpusState();

		s.numArrows = STARTING_ARROWS;
		s.currRoom = randomRoom();
		
		s.hazards = new ArrayList<Set<Hazard>>();
		for (int i = 0; i < rooms.length; i++) {
			s.hazards.add(new HashSet<Hazard>());
		}
 
		placeHazard(Hazard.Wumpus, false);
		placeHazard(Hazard.Bat, false);
		placeHazard(Hazard.Bat, false);
		placeHazard(Hazard.Pit, false);
		placeHazard(Hazard.Pit, false);
    }

	// +--------+
	// | prompt |
	// +--------+

	public String prompt() {

		// current location and routes
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("You are in room %d; Passages lead to rooms %d, %d and %d.",
								s.currRoom, links[s.currRoom][0],
								links[s.currRoom][1], links[s.currRoom][2]));

		// look at neighbors to see if there are hazards around
		HashSet seenHazards = new HashSet<Hazard>();
		for (int link : links[s.currRoom]) {
			for (Hazard hazard : s.hazards.get(link)) {
				if (!seenHazards.contains(hazard)) {
					seenHazards.add(hazard);
					sb.append("\n").append(hazard.warning);
				}
			}
		}

		return(sb.toString());
	}
	
	// +--------+
	// | action |
	// +--------+

	public String action(String input) {

		String cleanInput = input.trim().toLowerCase();
		char firstChar = cleanInput.charAt(0);

		// just a room number is shortcut for move
		if (Character.isDigit(firstChar)) {
			cleanInput = "move " + cleanInput;
			firstChar = cleanInput.charAt(0);
		}

		// shhh
		if (cleanInput.equals("xyzzy")) {
			try { return(serializeState()); }
			catch (Exception e) { return("Exception: " + e.toString()); }
		}

		if (firstChar == 'h' || firstChar == 'p') {
			return(help());
		}

		if (firstChar == 'l') {
			return(null);
		}
		
		if (firstChar == 'q' || firstChar == 'e' || firstChar == 'r') {
			// quit, exit, reset
			newGame();
			return("Game restarted!");
		}

		if (firstChar == 's' || firstChar == 'm') {
			// shoot, move ... this nesting is a little ugly but it
			// lets us fall out to the error condition in a few different
			// cases so seems a reasonable approach
			int ichSpace = cleanInput.indexOf(' ');
			if (ichSpace != -1) {
				try {
					int roomParam = Integer.parseInt(cleanInput.substring(ichSpace + 1));
					if (neighbor(roomParam)) {
						return(firstChar == 's' ? shoot(roomParam) : move(roomParam));
					}
					else {
						return("You can't get there from here.");
					}
				}
				catch (NumberFormatException e) {
					// fall through to error
				}
			}
		}

		// get here on unknown command or bad parameter
		return(confused());
	}

	// +------+
	// | help |
	// +------+

	public String help() {
		return("Welcome to Hunt the Wumpus! There are many versions of this " +
			   "game; ours follows the rules at " +
			   "https://rosettacode.org/wiki/Hunt_the_Wumpus.\n" +
			   "Good luck! Commands can be shortened to their first letter; " +
			   "just entering a number implies a move:\n" +
			   "- [s]hoot ROOM_NUMBER\n" +
			   "- [m]ove ROOM_NUMBER\n" +
			   "- [l]ook around\n" +
			   "- [r]estart the game\n" +
			   "- [h]elp");
	}
	
	private String confused() {
		return("Sorry, didn't catch that. Here's some help:\n\n" + help());
	}
	
	// +-----------------+
	// | move & evaluate |
	// +-----------------+

	private String move(int newRoom) {
		s.currRoom = newRoom;
		String result = evaluate();
		return(result.isEmpty() ? "Moved." : result);
	}
	
	private String evaluate() {

		if (roomHasHazard(s.currRoom, Hazard.Wumpus)) {
			newGame();
			return("You've been eaten by the Wumpus! Let's try that again.");
		}

		if (roomHasHazard(s.currRoom, Hazard.Pit)) {
			newGame();
			return("You fell into a pit! Let's try that again.");
		}

		if (roomHasHazard(s.currRoom, Hazard.Bat)) {

			// teleport to a new room without a bat (so we don't teleport again)
			int oldRoom = s.currRoom;
			s.currRoom = randomRoom();
			while (s.currRoom == oldRoom || roomHasHazard(s.currRoom, Hazard.Bat)) {
				s.currRoom = randomRoom();
			}

			// move the bat to a new room that's not this one; neighbors are ok
			// doing the remove second ensures it moves from its original location.
			placeHazard(Hazard.Bat, true);
			removeHazard(oldRoom, Hazard.Bat);

			// now see what happens in the this room
			return("A bat dropped you in a random room.\n" + evaluate());
		}

		return("");
	}
	
	// +-------+
	// | shoot |
	// +-------+

	private String shoot(int targetRoom) {

		if (roomHasHazard(targetRoom, Hazard.Wumpus)) {
			newGame();
			return("You shot the Wumpus! I think there are more; let's try again.");
		}

		if (--s.numArrows == 0) {
			newGame();
			return("You ran out of arrows. Let's try that again.");
		}

		// missed; 25% chance the wumpus stays put
		if (rand.nextInt(4) == 0) {
			return("Missed!");
		}

		// move the wumpus to a neighbor (he might eat us)
		
		int oldWump = wumpusRoom();
		int newWump = links[oldWump][rand.nextInt(3)];
			
		if (newWump == s.currRoom) {
			newGame();
			return("You woke the Wumpus and he ate you! Let's try that again.");
		}

		removeHazard(oldWump, Hazard.Wumpus);
		addHazard(newWump, Hazard.Wumpus);

		return("You woke the Wumpus and he moved.");
	}

	// +---------+
	// | Helpers |
	// +---------+

	private int randomRoom() {
		return(rand.nextInt(rooms.length));
	}
	
	private boolean roomHasHazard(int room, Hazard hazard) {
		return(s.hazards.get(room).contains(hazard));
	}

	private boolean in(int room) {
		return(s.currRoom == room);
	}

	public boolean neighbor(int room) {
		for (int link : links[s.currRoom]) {
			if (link == room) return(true);
		}
		return(false);
	}

	private void placeHazard(Hazard hazard, boolean neighborsOK) {

		// place > 1 room away
		// note distinct hazards can share rooms 

		while (true) {
			
			int room = randomRoom();
			
			if (!in(room) &&
				(neighborsOK || !neighbor(room)) &&
				!roomHasHazard(room, hazard)) {
			
				addHazard(room, hazard);
				return;
			}
		}
	}

	private void addHazard(int room, Hazard hazard) {
		s.hazards.get(room).add(hazard);
	}

	private void removeHazard(int room, Hazard hazard) {
		s.hazards.get(room).remove(hazard);
	}

	private int wumpusRoom() {

		for (int room = 0; room < rooms.length; ++room) {
			if (roomHasHazard(room, Hazard.Wumpus)) return(room);
		}

		// um what?
		return(-1);
	}

	// +------------------+
	// | World Definition |
	// +------------------+

    private static final Random rand = new Random();

	private static final ObjectMapper objectMapper = new ObjectMapper()
		.enable(SerializationFeature.INDENT_OUTPUT)
		.findAndRegisterModules(); 

	private static final int STARTING_ARROWS = 3;

    private static int[][] rooms =
	{{334, 20}, {609, 220}, {499, 540}, {169, 540}, {62, 220},
    {169, 255}, {232, 168}, {334, 136}, {435, 168}, {499, 255}, {499, 361},
    {435, 447}, {334, 480}, {232, 447}, {169, 361}, {254, 336}, {285, 238},
    {387, 238}, {418, 336}, {334, 393}};
 
    private static int[][] links =
	{{4, 7, 1}, {0, 9, 2}, {1, 11, 3}, {4, 13, 2}, {0, 5, 3},
    {4, 6, 14}, {7, 16, 5}, {6, 0, 8}, {7, 17, 9}, {8, 1, 10}, {9, 18, 11},
    {10, 2, 12}, {13, 19, 11}, {14, 3, 12}, {5, 15, 13}, {14, 16, 19},
    {6, 17, 15}, {16, 8, 18}, {19, 10, 17}, {15, 12, 18}};

	// +-----------------------+
	// | Standalone entrypoint |
	// +-----------------------+

	public static void main(String[] args) throws Exception {

		Scanner scanner = null;

		try {
			Wumpus wumpus = ((args.length == 0)
							 ? new Wumpus()
							 : Wumpus.fromSerializedState(args[1]));

			System.out.println(wumpus.help());
			System.out.println("");
			System.out.println(wumpus.prompt());
			System.out.print("> ");
			System.out.flush();

			scanner = new Scanner(System.in);

			while (true) {

				String line = scanner.nextLine();
				
				System.out.println("");

				String result = wumpus.action(line);
				if (result != null) {
					System.out.println(result);
					System.out.println("");
				}
				
				System.out.println(wumpus.prompt());
				System.out.print("> ");
				System.out.flush();
			}
		}
		finally {
			
			if (scanner != null) scanner.close();
		}
	}
}
