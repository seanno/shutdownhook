
import styles from './ResultsTables.module.css';

export default function ResultsTables({ results }) {

  // +--------------+
  // | renderResult |
  // +--------------+

  function renderResult(result, index) {

	if (!result.Rows || result.Rows.length === 0) {
	  
	  const msg = result.UpdateCount
			? `${result.UpdateCount} row${result.UpdateCount > 1 ? 's' : ''} affected`
			: 'no results';
	
	  return(<div key={`res${index}`} className={styles.updateResult}>{msg}</div>);
	}
	
	const headerCells = result.Headers.map((hdr, ihdr) => {
	  return(
		<th key={`hdr-${ihdr}`}>{hdr}</th>
	  );
	});
											
	const bodyRows = result.Rows.map((r, irow) => {
	  return(
		<tr key={`row-${irow}`}>
		  { r.map((cell, icell) => <td key={`cell-${icell}`}>{cell}</td>) }
		</tr>
	  );
	});

	const truncatedRow = (result.Truncated
			  ? <td colspan={result.Headers.length}><i>results truncated after {result.Rows.length} rows</i></td>
			  : undefined);

	return(
	  <table key={`res${index}`} className={styles.results}>
		{ result.Label && <caption>{result.Label}</caption> }
		<thead><tr>{ headerCells }</tr></thead>
		<tbody>{ bodyRows }{ truncatedRow }</tbody>
	  </table>
	);
  }

  // +-------------+
  // | Main Render |
  // +-------------+

  const elts = (results.Results && results.Results.length > 0
				? results.Results.map(renderResult)
				: <div className={styles.updateResult}>no results</div>);
  
  return(<div className={styles.container}>{ elts }</div>);
  
}

