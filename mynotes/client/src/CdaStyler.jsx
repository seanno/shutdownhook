import { useEffect, useState } from 'react'

export default function CdaStyler({ xmlText }) {

  const [html, setHtml] = useState(undefined);

  // +--------+
  // | effect |
  // +--------+

  useEffect(() => {

	fetch('cda-web.xsl')
	  .then(response => response.text())
	  .then(xslText => {
		
		const parser = new DOMParser();
		const xslDoc = parser.parseFromString(xslText, "application/xml");
		const xmlDoc = parser.parseFromString(xmlText, "application/xml");

		const xslt = new XSLTProcessor();
		xslt.importStylesheet(xslDoc);
		const styledDoc = xslt.transformToDocument(xmlDoc);

		const serializer = new XMLSerializer();
		var styledHtml = serializer.serializeToString(styledDoc);

		// https://stackoverflow.com/questions/42475012/how-to-make-href-anchors-in-iframe-srcdoc-actually-work
		styledHtml = styledHtml.replaceAll('href="#', 'href="about:srcdoc#');
		
		setHtml(styledHtml);
	  });
	
  }, []);

  // +-------------+
  // | Main Render |
  // +-------------+

  return(
	<iframe
	  srcdoc={html}
	  src='frame.html'
	  width="100%"
	  height="98%"
	  style={{ border: 'none', margin: '0px', padding: '0px' }} >
	</iframe>
  );
  
}

