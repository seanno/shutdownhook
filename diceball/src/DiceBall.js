
import { useState, useEffect } from 'react';
import Scoreboard from "./Scoreboard.js";
import OutsDisplay from "./OutsDisplay.js";
import ButtonBar from "./ButtonBar.js";

export default function DiceBall() {

  // +-------------------+
  // | State & Constants |
  // +-------------------+

  const HOME = "h";
  const VISITOR = "v";

  const VISITING_TEAMS = [
	"Orioles", "Red Sox", "White Sox", "Guardians", "Tigers", "Astros", "Royals",
	"Angels", "Twins", "Yankees", "Athletics", "Rays", "Rangers", "Blue Jays",
	"Diamondbacks", "Braves", "Cubs", "Reds", "Rockies", "Dodgers", "Marlins", "Brewers",
	"Mets", "Phillies", "Pirates", "Padres", "Giants", "Cardinals", "Nationals"
  ];

  const HOME_TEAM = "Mariners";
  
  const [visitingTeam, setVisitingTeam] = useState(localStorageString("visitor", randomVisitor()));
  const [outs, setOuts] = useState(localStorageInt("outs", 0));
  const [innings, setInnings] = useState(localStorageArray("innings", [0]));
  const [actionHistory, setActionHistory] = useState(localStorageArray("history", []));

  useEffect(() => {
	
	localStorage.setItem("visitor", visitingTeam);
	localStorage.setItem("outs", outs.toString());
	localStorage.setItem("innings", JSON.stringify(innings));
	localStorage.setItem("history", JSON.stringify(actionHistory));
	
  }, [visitingTeam,outs,innings,actionHistory]);
  
  // +---------+
  // | Actions |
  // +---------+

  const OUT_ACTION = "o";
  const RUN_ACTION = "r";
  
  const out = async () => {

	if (winner()) return;

	const newActionsHistory = actionHistory.slice();
	newActionsHistory.push(OUT_ACTION);
	setActionHistory(newActionsHistory);

	if (winnerPeek(outs + 1) || outs < 2) {
	  setOuts(outs + 1);
	}
	else {
	  setOuts(0);

	  const newInnings = innings.slice();
	  newInnings.push(0);
	  setInnings(newInnings);
	}
  }

  const run = async () => {
	
	if (winner()) return;
	
	const newActionsHistory = actionHistory.slice();
	newActionsHistory.push(RUN_ACTION);
	setActionHistory(newActionsHistory);

	const newInnings = innings.slice();
	newInnings[newInnings.length - 1]++;
	setInnings(newInnings);
  }

  const undo = async() => {
	
	if (actionHistory.length === 0) return;

	const newActionsHistory = actionHistory.slice();
	const undoAction = newActionsHistory.pop();
	setActionHistory(newActionsHistory);
	
	if (undoAction === OUT_ACTION) {
	  
	  if (outs > 0) {
		// easy
		setOuts(outs - 1);
	  }
	  else {
		// need to back up to the last half inning. There can't be
		// runs in this inning if we're doing this, so don't worry
		// about that.
		setOuts(2);

		const newInnings = innings.slice();
		newInnings.pop();
		setInnings(newInnings);
	  }
	}
	else {
	  // there *must* be a run in this inning if this undo action is a run,
	  // so just decrement runs in the current inning
	  const newInnings = innings.slice();
	  newInnings[newInnings.length - 1]--;
	  setInnings(newInnings);
	}
  }
  
  const reset = async() => {
	setVisitingTeam(randomVisitor());
	setOuts(0);
	setInnings([0]);
	setActionHistory([]);
  }

  // +---------+
  // | Helpers |
  // +---------+

  const winner = () => {
	return(winnerPeek(outs));
  }
  
  const winnerPeek = (o) => {

	// have to play at least eight full innings
	if (innings.length <= 16) return(undefined);

	const v = score(VISITOR);
	const h = score(HOME);

	// game is over after top half if home is ahead; otherwise play on
	if (teamAtBat() === VISITOR) {
	  return(o === 3 && h > v ? HOME : undefined);
	}

	// in bottom half. Game is over if home is ever ahead
	if (h > v) return(HOME);

	// also over if bottom is over and visitor is ahead
	if (o === 3 && v > h) return(VISITOR);

	// play on
	return(undefined);
  }

  const score = (team) => {

	let i = (team === VISITOR ? 0 : 1);
	let score = 0;
	
	while (i < innings.length) {
	  score += innings[i];
	  i += 2;
	}

	return(score);
  }

  const runsInInning = (team, inning) => {

	const i = (team === HOME ? 1 : 0) + ((inning - 1) * 2);
	return(i < innings.length ? innings[i] : undefined);
  }

  const currentInning = () => {
	return(Math.ceil(innings.length / 2));
  }

  const teamAtBat = () => {
	return((innings.length % 2 === 1) ? VISITOR : HOME);
  }

  const teamName = (team) => {
	return(team === HOME ? HOME_TEAM : visitingTeam);
  }

  // +-----------------+
  // | Storage Helpers |
  // +-----------------+

  function randomVisitor() {
	return(VISITING_TEAMS[Math.floor(Math.random() * VISITING_TEAMS.length)]);
  }

  function localStorageString(key, def) {
	return(localStorage.getItem(key) || def);
  }
  
  function localStorageArray(key, def) {

	const s = localStorage.getItem(key);
	if (!s) return(def);

	return(JSON.parse(s));
  }

  function localStorageInt(key, def) {

	const s = localStorage.getItem(key);
	if (!s) return(def);

	return(parseInt(s));
  }

  // +--------+
  // | render |
  // +--------+

  const actions = {
	out: out,
	run: run,
	undo: undo,
	reset: reset
  }

  const helpers = {
	outs: outs,
	winner: winner(),
	currentInning: currentInning(),
	teamAtBat: teamAtBat(),

	homeTeamName: teamName(HOME),
	visitingTeamName: teamName(VISITOR),
	
	runsInInning: runsInInning,
	score: score,

	HOME: HOME,
	VISITOR: VISITOR
  }

  return (
	<div>
	  <Scoreboard helpers={helpers} />
	  <ButtonBar actions={actions} />
	  <OutsDisplay helpers={helpers} />
	</div>
  );
}

