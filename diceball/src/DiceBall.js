
import { Button } from '@mui/material';
import Scoreboard from "./Scoreboard.js";
import GameState from "./lib/GameState.js";

export default function DiceBall() {

  const game = new GameState();
  
  return (
	<div>
	  <Scoreboard game={game} />
	</div>
  );
}

