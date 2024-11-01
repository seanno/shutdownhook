import { useState } from 'react'

export default function AppHeader({ fhir }) {

  return (
	<>
	  <img src="explain.png" />
	  <br/><br/>
	  { fhir && <a href="/">X</a> }
    </>
  )
}

