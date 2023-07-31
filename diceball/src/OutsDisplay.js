
import RadioButtonUncheckedIcon from '@material-ui/icons/RadioButtonUnchecked';
import Brightness1Icon from '@material-ui/icons/Brightness1';
import styles from './OutsDisplay.module.css';

export default function OutsDisplay({ helpers }) {

  if (helpers.winner) {

	const team = (helpers.winner === helpers.HOME
				  ? helpers.homeTeamName : helpers.visitingTeamName);
	
	return(
	  <div className={styles.container}>
		<span>{team} win!</span>
	  </div>
	);
  }
  
  const button1 = (helpers.outs >= 1
				   ? <Brightness1Icon /> : <RadioButtonUncheckedIcon/>);
  
  const button2 = (helpers.outs >= 2
				   ? <Brightness1Icon /> : <RadioButtonUncheckedIcon/>);
				   
  const button3 = (helpers.outs >= 3
				   ? <Brightness1Icon /> : <RadioButtonUncheckedIcon/>);

  return (
    <div className={styles.container}>
	  <span>Outs</span>
	  { button1 }{ button2 }{ button3 }
    </div>
  );
}

