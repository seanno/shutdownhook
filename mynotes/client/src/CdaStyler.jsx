import { useEffect, useState } from 'react'

export default function CdaStyler({ xmlText }) {

  const [html, setHtml] = useState(undefined);

  // +--------+
  // | effect |
  // +--------+

  useEffect(() => {

	fetch('cda/CDA.xsl')
	  .then(response => response.text())
	  .then(xslText => {
		
		const parser = new DOMParser();
		const xslDoc = parser.parseFromString(xslText, "application/xml");
		const xmlDoc = parser.parseFromString(xmlText, "application/xml");

		const xslt = new XSLTProcessor();
		xslt.importStylesheet(xslDoc);
		const styledDoc = xslt.transformToDocument(xmlDoc);

		const serializer = new XMLSerializer();
		setHtml(serializer.serializeToString(styledDoc));
	  });
	
  }, []);

  // +-------------+
  // | Main Render |
  // +-------------+

  return(
	<div style={{ height: '100%', width: '100%' }}
		 dangerouslySetInnerHTML={{ __html: html }}></div>
  );
  
}

