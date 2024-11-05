
import styles from './Intro.module.css'

export default function Intro() {

  return(
	
	  <div className={styles.container}>

		<div className={styles.intro}
			 style={{ gridRow: '1', gridColumn: '1 / 3' }}>
		  
		  <p>
			Thanks to a lot of hard work by a lot of folks, most of us have
			access to the clinical notes written during our healthcare visits.
			And that's great, but notes are full of medical terms and jargon
			that can be pretty tough to understand. This site tries to help
			with that using the magic of artificial intelligence
			and <a href="https://chatgpt.com/" target="_blank">ChatGPT</a>).
			Please read the below before getting started.
		  </p>
		</div>

		<div className={styles.detail}
			 style={{ gridRow: '2', gridColumn: '1' }}>
			  
		  <h2>How it works</h2>
		  <p>
			Use the search box at the top of this page to find your healthcare
			provider, then use your patient portal account to log in and grant
			access. We'll show a list of your visits and the documents associated
			with each.
			</p>
		  <p>
			Click the "explain" link above any document, and we'll ask ChatGPT
			to summarize and explain its contents using terms that make sense
			to those of us without a medical degree. Use the tabs to flip
			back and forth between the original and "translated" document.
		  </p>
		  <p>
			<span style={{ color: 'red' }}><b>Important!</b></span> AI is impressive
			but it is <b>not perfect</b> and at times will be <b>wrong</b>. Think 
			of ChatGPT as your well-read but untrained and eager-to-please
			colleague. <b>Never</b> rely on anything you find here without speaking to your
			doctor first.
		  </p>
		  
		</div>

		<div className={styles.detail}
			 style={{ gridRow: '2', gridColumn: '2' }}>
		  
		  <h2>Who can see my information?</h2>
		  <p>
			<span style={{ color: 'red' }}><b>Also important!</b></span> When
			you connect this site to your healthcare provider, the provider collects your login
			information and will show you exactly what types of data will be shared
			with us. 
		  </p>
		  <p>
			As you browse encounters and view original documents, none of
		    that information is visible to us. Everything stays in your browser, on your
			device, until you click the "explain" tab to get a summary.
		  </p>
		  <p>
			When you choose "explain," the contents of the current document are sent
			over the Internet to our server (a physical computer in my basement) and then
			forwarded on to OpenAI servers where it can be processed by ChatGPT.
		  </p>
		  <p>
			Your data are <b>never saved on our servers</b>; they exist there only for the
			duration of the call to OpenAI. OpenAI has (in our opinion) a reasonable set
			of policies for securing and using data, but they are <b>not</b> under our
			control. Please read the OpenAI privacy policy and terms of use for
			yourself <b>before using the explain feature</b>:
		  </p>
		  <ul>
			<li><a target="_blank"
				   href="https://openai.com/policies/privacy-policy/">OpenAI Privacy policy</a></li>
			<li><a target="_blank"
				   href="https://openai.com/policies/terms-of-use/">OpenAI Terms of use</a></li>
		  </ul>
		  <p>
			Most importantly, <b>neither we nor OpenAI are HIPAA covered entities
			or business associates of any healthcare provider</b>. In the United States you have the
			right to share your personal healthcare data with anyone you choose, including this site
			and OpenAI, but doing so is your responsibility and you accept the risks of doing so. Enough said.
		  </p>
		  
		</div>
		
	  </div>
   );
}

