
import { Button } from '@mui/material';
import styles from './ButtonBar.module.css';

export default function ButtonBar({ actions }) {

  return (
    <div className={styles.container}>
	  <Button sx={{ m: 1 }} variant="contained" onClick={actions.out}>Out</Button>
	  <Button sx={{ m: 1 }} variant="contained" onClick={actions.run}>Run</Button>
	  <Button sx={{ m: 1 }} variant="contained" onClick={actions.undo}>Undo</Button>
    </div>
  );
}

