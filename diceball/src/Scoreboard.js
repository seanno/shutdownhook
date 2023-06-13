
import styles from './Scoreboard.module.css';

export default function Scoreboard({ helpers }) {

  const currentInning = helpers.currentInning;
  const totalInnings = (currentInning > 9 ? currentInning : 9);

  const inningElts = [];
  for (let i = 1; i <= totalInnings; ++i) {

	let visitClass = styles.visitingInning;
	let homeClass = styles.homeInning;

	if (!helpers.winner && i === currentInning && helpers.outs < 3) {
	  if (helpers.teamAtBat === helpers.VISITOR) {
		visitClass += " " + styles.atbat;
	  }
	  else {
		homeClass += " " + styles.atbat;
	  }
	}

	inningElts.push(
	  <div key={'hdi'+i} className={styles.headerInning} style={{ gridColumn: i + 1 }}>
		{ i }
	  </div>);

	inningElts.push(
	  <div key={'vti'+i} className={visitClass} style={{ gridColumn: i + 1 }}>
		{ helpers.runsInInning(helpers.VISITOR, i) }
	  </div>);

	inningElts.push(
	  <div key={'hti'+i} className={homeClass} style={{ gridColumn: i + 1 }}>
		{ helpers.runsInInning(helpers.HOME, i) }
	  </div>);
  }

  return (
    <div className={styles.container}>
	  <div className={styles.homeTeam}>{helpers.homeTeamName}</div>
	  <div className={styles.visitingTeam}>{helpers.visitingTeamName}</div>
	  { inningElts }

	  <div className={styles.headerInning} style={{ gridColumn: totalInnings + 3 }}>
		R
	  </div>

	  <div className={styles.visitingInning} style={{ gridColumn: totalInnings + 3 }}>
		{helpers.score(helpers.VISITOR)}
	  </div>

	  <div className={styles.homeInning} style={{ gridColumn: totalInnings + 3 }}>
		{helpers.score(helpers.HOME)}
	  </div>

    </div>
  );
}

