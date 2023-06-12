
import GameState from "./lib/GameState.js";
import styles from './Scoreboard.module.css';

export default function Scoreboard({ game }) {

  const currentInning = game.currentInning();
  const totalInnings = (currentInning > 9 ? currentInning : 9);

  const inningElts = [];
  for (let i = 1; i <= totalInnings; ++i) {

	let visitClass = styles.visitingInning;
	let homeClass = styles.homeInning;

	if (i === currentInning && game.outs < 3) {
	  if (game.teamAtBat() === GameState.VISITOR) visitClass += " " + styles.atbat;
	  else homeClass += " " + styles.atbat;
	}

	inningElts.push(
	  <div key={'hdi'+i} className={styles.headerInning} style={{ gridColumn: i + 1 }}>
		{ i }
	  </div>);

	inningElts.push(
	  <div key={'vti'+i} className={visitClass} style={{ gridColumn: i + 1 }}>
		{ game.runsInInning(GameState.VISITOR, i) }
	  </div>);

	inningElts.push(
	  <div key={'hti'+i} className={homeClass} style={{ gridColumn: i + 1 }}>
		{ game.runsInInning(GameState.HOME, i) }
	  </div>);
  }

  return (
    <div className={styles.container}>
	  <div className={styles.homeTeam}>{game.teamName(GameState.HOME)}</div>
	  <div className={styles.visitingTeam}>{game.teamName(GameState.VISITOR)}</div>
	  { inningElts }
    </div>
  );
}

