<script lang="ts">
  import Header from "$components/features/Header.svelte"
  import PageOutline from "$components/features/PageOutline.svelte"
  import type { Readable } from "svelte/store"
  import type { I18nGetter } from "$lib/i18n"
  import { Tabs } from "$components/atoms/tabs"
  import type { HeaderButtonAction } from "$components/interfaces/HeaderButtonAction"
  import Footer from "$components/features/Footer.svelte"

  interface Props {
    i18n: Readable<I18nGetter>
    headerButtonAction?: HeaderButtonAction
    isDeveloperTerms: Boolean
  }

  let { i18n, headerButtonAction, isDeveloperTerms }: Props = $props()
  let value: string = $state(isDeveloperTerms ? "developerTerms" : "userTerms")
</script>

<Header {i18n} buttonAction={headerButtonAction} />
<PageOutline>
  {#snippet title()}
    <div class="px-4 pt-8">
      <h3 class="h3 text-c-text-negative">{$i18n("termsPage.title")}</h3>
    </div>
  {/snippet}
  {#snippet children()}
    <Tabs.Root {value}>
      <Tabs.List
        tabs={[
          { title: "User Terms", id: "userTerms" },
          { title: "Developer Terms", id: "developerTerms" },
        ]}
      ></Tabs.List>
      <Tabs.Content value="userTerms">
        <div id="userTerms">
          <div class="py-6">
            <div>
              <p><b>Last Updated: October 23, 2024</b></p>
              <br />
              <div>
                <ul class="*:pb-4 *:pl-4 *:underline">
                  <li>
                    <a href="#agreement-to-terms">1. Agreement to Terms and Privacy Notice</a>
                  </li>
                  <li>
                    <a href="#changes-to-terms">2. Changes to these Terms of the Services</a>
                  </li>
                  <li>
                    <a href="#eligibility">3. Eligibility</a>
                  </li>
                  <li>
                    <a href="#acknowledgment-of-development-status"
                      >4. Acknowledgment of Development Status of the Services</a
                    >
                  </li>
                  <li>
                    <a href="#functionality">5. Functionality</a>
                  </li>
                  <li>
                    <a href="#account-credentials-security">6. Account, Credentials and Security</a>
                  </li>
                  <li>
                    <a href="#feedback">7. Feedback</a>
                  </li>
                  <li>
                    <a href="#social-graph">8. Social Graph</a>
                  </li>
                  <li>
                    <a href="#general-prohibitions">9. General Prohibitions and Project Liberty's Enforcement Rights</a>
                  </li>
                  <li>
                    <a href="#disclaimers-risks">10. Disclaimers; Risks</a>
                  </li>
                  <li>
                    <a href="#indemnity">11. Indemnity</a>
                  </li>
                  <li>
                    <a href="#limitation-on-liability">12. Limitation on Liability</a>
                  </li>
                  <li>
                    <a href="#limited-license">13. Limited License</a>
                  </li>
                  <li>
                    <a href="#links">14. Links</a>
                  </li>
                  <li>
                    <a href="#termination-and-suspension">15. Termination and Suspension</a>
                  </li>
                  <li>
                    <a href="#no-third-party-beneficiaries">16. No Third Party Beneficiaries</a>
                  </li>
                  <li>
                    <a href="#governing-law-and-forum-choice">17. Governing Law and Forum Choice</a>
                  </li>
                  <li>
                    <a href="#dispute-resolution">18. Dispute Resolution</a>
                  </li>
                  <li>
                    <a href="#general-terms">19. General Terms</a>
                  </li>
                  <li>
                    <a href="#contact-information">20. Contact Information</a>
                  </li>
                </ul>
              </div>
            </div>
            <p>
              Please read these Frequency Access Terms of Service (these “Terms”) and our <a href="privacy.html"
                >Privacy Notice</a
              >
              (“<b>Privacy Notice</b>”) carefully because they govern your use of Frequency Access, a software
              application offered to you by Project Liberty LLC which enables users to authenticate to, set permissions
              and interact with Supported Apps and Supported Networks (each as are defined below) as more fully
              described in these Terms, accessible through the Supported Apps' applicable websites and/or mobile
              applications via application program interfaces and browser plugins and our websites and/or mobile
              applications.
            </p>
            <p>To make these Terms easier to read:</p>
            <ol class="list-disc">
              <li>
                Project Liberty LLC is referred to as “<b>Project Liberty</b>”, “<b>we</b>,” “<b>us</b>,” or “<b>our</b
                >.”
              </li>
              <li>Frequency Access, as well as the associated API and browser plugins that connect to Supported</li>
              <li>
                Apps, websites, mobile applications and any other features, tools, materials, and other services offered
                from time to time by Project Liberty are referred to collectively as the “Services.”
              </li>
              <li>
                “<b>You</b>” and “<b>your</b>” refer to you, as a user of the Services. A “<b>user</b>” is someone who
                accesses or in any way uses the Services.
              </li>
            </ol>
            <p>
              <b>
                IMPORTANT NOTICE REGARDING ARBITRATION: WHEN YOU AGREE TO THESE TERMS YOU ARE AGREEING (WITH LIMITED
                EXCEPTION) TO RESOLVE ANY DISPUTE BETWEEN YOU AND PROJECT LIBERTY THROUGH BINDING, INDIVIDUAL
                ARBITRATION RATHER THAN IN COURT. PLEASE REVIEW CAREFULLY SECTION 18 BELOW FOR DETAILS REGARDING
                ARBITRATION. HOWEVER, IF YOU ARE A RESIDENT OF A JURISDICTION WHERE APPLICABLE LAW PROHIBITS ARBITRATION
                OF DISPUTES, THE AGREEMENT TO ARBITRATE IN SECTION 18 WILL NOT APPLY TO YOU BUT THE PROVISIONS OF
                SECTION 18 (GOVERNING LAW AND FORUM CHOICE) WILL APPLY INSTEAD.
              </b>
            </p>
          </div>
          <div class="-pt-0.5">
            <div>
              <ul class="*:pb-6">
                <li id="agreement-to-terms">
                  <h5 class="py-1">1. Agreement to Terms and Privacy Notice</h5>
                  <p>
                    By using or accessing the Services, you agree to be bound by these Terms as well as our Privacy
                    Notice which provides information on how we collect, use and share your information. If you don't
                    agree to be bound by these Terms or our Privacy Notice, do not use or access the Services. If you
                    are accessing or using the Services on behalf of a company (such as your employer) or other legal
                    entity, you represent and warrant that you have the authority to bind that entity to these Terms. In
                    that case, “<b>you</b>” and “<b>your</b>” will also refer to that entity.
                  </p>
                </li>
                <li id="changes-to-terms">
                  <h5 class="py-1">2. Changes to these Terms or the Services</h5>
                  <p>
                    We may update these Terms and the Services, from time to time, at our sole discretion. If we do, we
                    will let you know by providing the updated Terms through the Services. It's important that you
                    review these Terms whenever we update them or you use or access the Services. If you continue to use
                    or access the Services after we have provided updated Terms it means that you accept and agree to
                    the changes. If you don't agree to be bound by the changes, you may not use or access the Services
                    anymore. Because the Services are evolving over time we may change or discontinue all or any part of
                    the Services, at any time and without notice, at our sole discretion.
                  </p>
                </li>
                <li id="eligibility">
                  <h5 class="py-1">3. Eligibility</h5>
                  <p>
                    You may use the Services only if you are 16 years or older, are capable of forming a binding
                    contract with Project Liberty, and are not barred from using the Services under applicable law. If
                    you are under the age of majority in your jurisdiction, you must receive your parent's or guardian's
                    permission to use the Services, and “<b>you</b>” and “<b>your</b>” as used in these Terms also means
                    your parent or guardian.
                  </p>
                  <p>
                    By using or accessing the Services, you represent to us that you are: (1) not subject to sanctions,
                    embargoes, export restrictions or otherwise designated on any list of prohibited or restricted
                    parties, including but not limited to the lists maintained by the United Nations Security Council,
                    the U.S. Government (e.g., the Specially Designated Nationals List and Foreign Sanctions Evaders
                    List of the U.S. Department of Treasury and the Entity List of the U.S. Department of Commerce), the
                    European Union or its Member States, or other applicable government authority; (2) not located in
                    (or a citizen of) any jurisdiction to which the United States has embargoed goods or has otherwise
                    applied any sanctions; and (3) are otherwise authorized access to the Services under applicable law.
                  </p>
                </li>
                <li id="acknowledgment-of-development-status">
                  <h5 class="py-1">4. Acknowledgment of Development Status of the Services</h5>
                  <p>
                    You acknowledge and agree that: (a) the Services are still under development; (b) the Services are
                    not in final form and may not operate properly or be fully functional; (c) the Services may contain
                    errors, design flaws or other problems; (d) it may not be possible to make the Services fully
                    functional; (e) the information obtained using the Services may not be accurate; (f) use of the
                    Services may result in unexpected results, loss of data or communications, project delays or other
                    unpredictable damage or loss, and it is your responsibility to back up and restore any data that you
                    store in or transmit through the Services; (g) Project Liberty is under no obligation to release a
                    commercial version of the Services; and (h) Project Liberty has the right unilaterally to abandon
                    development of the Services, at any time and without any obligation or liability to you.
                  </p>
                </li>
                <li id="functionality">
                  <h5 class="py-1">5. Functionality</h5>
                  <p>
                    The Services enable you to interact with the Frequency chain, a layer 1 blockchain integrated into
                    the Polkadot platform, and/or other supported networks (together, the “<b>Supported Networks</b>”).
                    Through such interaction, you will be able to: (a) establish a message source account that allows
                    you to set certain permissions and access functions (the “<b>MSA</b>”); (b) store the digital
                    representation of your online relationships in the form of a social graph (the “<b>Social Graph</b
                    >”); (c) establish a self-selected, human readable identifier used to identify the user's MSA (“<b
                      >Handle</b
                    >”) and (d) interact with other applications, including such applications' services, content,
                    features, products, or other functionalities, that Project Liberty may, in our sole discretion,
                    elect to support through integration with our API, browser plugins, websites and/or mobile
                    applications (together, the “<b>Supported Apps</b>”). The Social Graph, Handle and other information
                    on the Supported Networks are tied to your MSA.
                  </p>
                  <p>
                    PROJECT LIBERTY IS NOT RESPONSIBLE FOR, AND CANNOT UNILATERALLY CREATE, MODIFY OR DELETE YOUR MSA,
                    SOCIAL GRAPH, HANDLE AND OTHER INFORMATION ON THE SUPPORTED NETWORKS.
                  </p>
                  <p>
                    THE SERVICES ARE NOT DESIGNED TO, AND YOU MAY NOT USE THE SERVICES TO, RECEIVE, HOLD, TRANSMIT, OR
                    TRANSFER CRYPTOCURRENCY OR OTHER CURRENCIES OR MONETARY OR FINANCIAL ASSETS. THE SERVICES AS OFFERED
                    BY PROJECT LIBERTY ARE ONLY IN CONNECTION WITH NON-FINANCIAL SOCIAL INTERACTION WITHIN A SOCIAL
                    NETWORK.
                  </p>
                </li>
                <li id="account-credentials-security">
                  <h5 class="py-1">6. Account, Credentials and Security</h5>
                  <ul class="*:px-4 *:py-2">
                    <li>
                      6.1. <u>Account and Access Credentials</u>. To access and use our Services, you will need to set
                      up an account. When setting up an account within the Services, a cryptographic private and public
                      key pair is generated, which evidence your ownership of your MSA and enables you to engage in
                      activities involving the Supported Networks and Supported Apps. The public key is visible to all
                      participants in the Supported Networks. The private key must be used to grant or revoke permission
                      for a Supported App to access your MSA and other associated information as represented by the
                      corresponding public key. Project Liberty encrypts your private key in a process designed to help
                      ensure its security and confidentiality. Project Liberty's proprietary software enables you to
                      initiate an approval or rejection of access to your MSA and other associated information using
                      your phone number, email address, or other access credential as further described below. Project
                      Liberty does not store your private key in its database. Project Liberty utilizes cloud based key
                      management services and hardware security modules to encrypt your private key in a process
                      designed to help ensure its security and confidentiality. Project Liberty cannot access raw
                      private key information for you, and we will not unilaterally initiate any such approvals or
                      rejections in connection with, or access, your MSA, Social Graph, Handle and other associated
                      information.
                      <b
                        >We are not responsible for any activities that you engage in when using the Services, you are
                        solely responsible for any and all approvals or rejections of access to your MSA, Social Graph,
                        Handle and other associated information that are initiated through the Services and we make no,
                        and hereby disclaim all, representations, warranties, claims and assurances as to any such
                        initiations of approvals or rejections.</b
                      > You agree to immediately notify Project Liberty of any unauthorized use of your account or breach
                      of security. You further represent and agree that Project Liberty shall not be liable for any loss
                      or damage arising from your failure to comply with this section.
                    </li>
                    <li>
                      6.2. <u>Accuracy of Account Information</u>. It's important that you provide us with accurate,
                      complete and current information for your account and you agree to keep this information up-
                      to-date. If you don't, we might have to suspend or terminate your account. You're responsible for
                      all activities that occur under your account. We may take actions we deem reasonably necessary to
                      prevent fraud and abuse, including placing restrictions on user accounts. If you lose access to
                      the phone number, email address, or other access credential used for your account, you may not be
                      able to recover access to your account.
                    </li>
                    <li>
                      6.3. <u>Compliance</u>. In the event of any prohibited use of the Services or our systems,
                      including using the Services to receive, hold, transmit, or transfer cryptocurrency or other
                      currencies or monetary or financial assets, you acknowledge and agree that Project Liberty, in its
                      sole discretion, may determine what, if any, actions Project Liberty will take, including
                      suspending or terminating your account, exporting any information that we hold on your behalf to
                      you, or giving you the option to receive such export, subject to applicable laws.
                    </li>
                  </ul>
                </li>
                <li id="feedback">
                  <h5 class="py-1">7. Feedback</h5>
                  <p>
                    We welcome feedback, comments and suggestions for improvements or additions to the Services (“<b
                      >Feedback</b
                    >”). You can submit Feedback by emailing us at
                    <a href="mailto:feedback@frequencyaccess.com" class="underline">feedback@frequencyaccess.com</a>
                    or through functionality that we may include in the Services. You grant us a non-exclusive, transferable,
                    worldwide, perpetual, irrevocable, fully-paid, royalty-free license, with the right to sublicense through
                    multiple tiers, under any and all intellectual property rights that you own or control to use, copy,
                    modify, create derivative works based upon and otherwise exploit the Feedback for any purpose, including,
                    but not limited to, the improvement of the Services and our other products, services and technologies.
                  </p>
                </li>
                <li id="social-graph">
                  <h5 class="py-1">8. Social Graph</h5>
                  <ul>
                    <li class="px-4">
                      8.1. Our Services enable you to interact with the Supported Networks to store your Social Graph.
                      The Social Graph may include data about your relation to other MSAs. Project Liberty may store
                      some information about your Social Graph. Project Liberty does not curate any portion of your
                      Social Graph nor claim any ownership rights in any portion of your Social Graph and nothing in
                      these Terms will be deemed to restrict any rights that you may have to your Social Graph.
                    </li>
                    <li class="px-4 pt-4">
                      8.2. <u>Permissions to Your Social Graph</u>. By making any Social Graph available through the
                      Services you hereby grant to Project Liberty a non-exclusive, transferable, worldwide, royalty-
                      free license, with the right to sublicense, to use, copy, modify, create derivative works based
                      upon, distribute, publicly display, and publicly perform your Social Graph solely for the purposes
                      of operating and providing the Services.
                    </li>
                  </ul>
                </li>
                <li id="general-prohibitions">
                  <h5 class="py-1">9. General Prohibitions and Project Liberty's Enforcement Rights.</h5>
                  You agree not to do any of the following:
                  <ul class="list-disc *:ml-8">
                    <li class="pt-2">
                      Use the Services to: (i) infringe, misappropriate or violate a third party's patent, copyright,
                      trademark, trade secret, moral rights or other intellectual property rights, or rights of
                      publicity or privacy; or (ii) violate, or encourage any conduct that would violate, any applicable
                      law or regulation or would give rise to civil liability;
                    </li>
                    <li>
                      Use the Services to submit information that: (i) is fraudulent, false, misleading or deceptive;
                      (ii) is defamatory, obscene, pornographic, vulgar or offensive; (iii) promotes discrimination,
                      bigotry, racism, hatred, harassment or harm against any individual or group; (iv) is violent or
                      threatening or promotes violence or actions that are threatening to any person or entity; or (v)
                      promotes illegal or harmful activities or substances;
                    </li>
                    <li>
                      Use, display, mirror or frame the Services or any individual element within the Services, Project
                      Liberty's name, any Project Liberty trademark, logo or other proprietary information, or the
                      layout and design of any page or form contained on a page, without Project Liberty's express
                      written consent; Access, tamper with, or use non-public areas of the Services, Project Liberty's
                      computer systems, or the technical delivery systems of Project Liberty's providers;
                    </li>
                    <li>
                      Attempt to probe, scan or test the vulnerability of any Project Liberty system or network or
                      breach any security or authentication measures;
                    </li>
                    <li>
                      Avoid, bypass, remove, deactivate, impair, descramble or otherwise circumvent any technological
                      measure implemented by Project Liberty or any of Project Liberty's providers or any other third
                      party (including another user) to protect the Services;
                    </li>
                    <li>
                      Attempt to access or search the Services or download content from the Services using any engine,
                      software, tool, agent, device or mechanism (including spiders, robots, crawlers, data mining tools
                      or the like) other than the software and/or search agents provided by Project Liberty or other
                      generally available third-party web browsers;
                    </li>
                    <li>
                      Send any unsolicited or unauthorized advertising, promotional materials, email, junk mail, spam,
                      chain letters or other form of solicitation;
                    </li>
                    <li>
                      Use any meta tags or other hidden text or metadata utilizing a Project Liberty trademark, logo URL
                      or product name without Project Liberty's express written consent;
                    </li>
                    <li>
                      Use the Services, or any portion thereof, for any commercial purpose or for the benefit of any
                      third party or in any manner not permitted by these Terms;
                    </li>
                    <li>
                      Forge any TCP/IP packet header or any part of the header information in any email or newsgroup
                      posting, or in any way use the Services to send altered, deceptive or false source- identifying
                      information;
                    </li>
                    <li>
                      Attempt to decipher, decompile, disassemble or reverse engineer any of the software used to
                      provide the Services;
                    </li>
                    <li>
                      Interfere with, or attempt to interfere with, the access of any user, host or network, including,
                      without limitation, sending a virus, overloading, flooding, spamming, or mail-bombing the
                      Services;
                    </li>
                    <li>
                      Collect or store any personally identifiable information from the Services from other users of the
                      Services without their express permission;
                    </li>
                    <li>
                      Impersonate or misrepresent your affiliation with any person or entity; or encourage or enable any
                      other individual to do any of the foregoing.
                    </li>
                  </ul>
                </li>
                <li id="disclaimers-risks">
                  <h5 class="py-1">10. Disclaimers; Risks</h5>
                  <ul class="*:px-4 *:py-2">
                    <li>
                      10.1. <u>Warranty Disclaimer</u>. YOU EXPRESSLY ACKNOWLEDGE AND AGREE THAT USE OF THE SERVICES
                      (INCLUDING ANY PRIVATE KEY STORAGE SERVICE OFFERED AS PART OF THE SERVICES, WHETHER CLOUD OR
                      HARDWARE-BASED) IS AT YOUR SOLE RISK AND THAT THE ENTIRE RISK AS TO SATISFACTORY QUALITY,
                      PERFORMANCE, ACCURACY AND EFFORT IS WITH YOU. THE SERVICES ARE PROVIDED “AS IS,” WITHOUT WARRANTY
                      OF ANY KIND. WITHOUT LIMITING THE FOREGOING, WE EXPLICITLY DISCLAIM ANY IMPLIED WARRANTIES OF
                      MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT AND NON-INFRINGEMENT, AND ANY
                      WARRANTIES ARISING OUT OF COURSE OF DEALING OR USAGE OF TRADE. WE MAKE NO WARRANTY THAT THE
                      SERVICES WILL MEET YOUR REQUIREMENTS OR BE AVAILABLE ON AN UNINTERRUPTED, SECURE, OR ERROR-FREE
                      BASIS. WE MAKE NO WARRANTY REGARDING THE QUALITY, ACCURACY, TIMELINESS, TRUTHFULNESS, COMPLETENESS
                      OR RELIABILITY OF ANY INFORMATION OR CONTENT ON THE SERVICES.
                    </li>
                    <li>
                      10.2. <u>Sophistication and Risk of Cryptographic Systems</u>. By using or accessing the Services,
                      you represent that you understand the inherent risks associated with cryptographic systems; and
                      warrant that you have an understanding of the usage and intricacies of cryptographic tokens,
                      decentralized networks, and blockchain-based software systems.
                    </li>
                    <li>
                      10.3. <u>Risk of Regulatory Actions in One or More Jurisdictions</u>. Project Liberty as well as
                      any other Supported Network and Supported App, could be impacted by one or more regulatory
                      inquiries or regulatory action, which could impede or limit the ability of Project Liberty to
                      continue to develop, or which could impede or limit your ability to access or use the Services,
                      the Frequency chain, any other Supported Network, MSA, Handle, Social Graph, and/or any Supported
                      App. By using or accessing the Services, you acknowledge these risks and agree that Project
                      Liberty will not be held liable for any losses or damages associated with these risks including,
                      without limitation, losses associated with your use of the Social Graph and Supported Networks.
                    </li>
                    <li>
                      10.4. <u>Risk of Weaknesses or Exploits in the Field of Cryptography</u>. You acknowledge and
                      understand that cryptography is a progressing field and that advances in cryptanalysis or
                      technical advances such as the development of quantum computers may present risks to
                      cryptocurrencies and the Services, which could result in the theft or loss of your MSA, Handle or
                      Social Graph. To the extent possible, Project Liberty intends to update the Services to account
                      for any advances in cryptography and to incorporate additional security measures but does not
                      guarantee or otherwise represent full security of the Services. By using or accessing the
                      Services, you acknowledge these risks and agree that Project Liberty will not be held liable for
                      any losses or damages associated with these risks including, without limitation, losses associated
                      with your use of the Supported Networks.
                    </li>
                    <li>
                      10.5. <u>Other Affiliated Services and Third Party Services.</u>
                      <ul class="list-disc *:ml-8">
                        <li class="pt-2">
                          The Services may include, integrate with, or be accessible through, Supported Apps including
                          software and services provided by our affiliates and or by third parties (“<b
                            >Third Party Services</b
                          >”). Supported Apps are made available to you under the terms of the applicable service
                          providers (collectively, “<b>Service Providers</b>”). Please review the applicable terms prior
                          to using or accessing Supported Apps. By using any Supported Apps, you acknowledge that (i)
                          you have read and agree to the terms that apply to such Supported Apps and (ii) you may be
                          exposed to the risks inherent in such Supported Apps. Such risks include, without limitation,
                          delays in or inability to access funds or cryptographic tokens held by such parties or loss of
                          funds of cryptographic tokens. You agree that Project Liberty is not responsible for any such
                          liability.
                        </li>
                        <li>Service Providers may charge you a fee for use of, or access to, Supported Apps.</li>
                        <li>
                          You agree that Project Liberty and its affiliates are not in any way associated with the Third
                          Party Services or responsible or liable for the software and services offered by the
                          associated Service Providers. Project Liberty does not endorse or approve and makes no
                          warranties, representations or undertakings relating to the software, service or content of
                          any Third Party Services.
                        </li>
                        <li>
                          In addition, Project Liberty disclaims liability for any loss, damage and any other
                          consequence resulting directly or indirectly from or relating to your use or access of Third
                          Party Services or any information that you may provide or any transaction conducted with or
                          through the Third Party Services or the failure of any information, software or services
                          posted or offered by such Service Providers or any error, omission or misrepresentation by
                          such Service Providers or any computer virus arising from or system failure associated with
                          the Third Party Services.
                        </li>
                        <li>
                          In the event of any inconsistency between these Terms and the terms of the Supported Apps,
                          these Terms will prevail with regard to any matter concerning Project Liberty's provision of
                          the Services to you.
                        </li>
                      </ul>
                    </li>
                  </ul>
                </li>
                <li id="indemnity">
                  <h5 class="py-1">11. Indemnity</h5>
                  <p>
                    You will indemnify and hold Project Liberty and its officers, directors, employees and agents,
                    harmless from and against any claims, disputes, demands, liabilities, damages, losses, and costs and
                    expenses, including, without limitation, reasonable legal and accounting fees arising out of or in
                    any way connected with (a) your access to or use of the Services, (b) your MSA, Handle or Social
                    Graph or (c) your violation of these Terms.
                  </p>
                </li>
                <li id="limitation-on-liability">
                  <h5 class="py-1">12. Limitation on Liability</h5>
                  <ul>
                    <li class="px-4">
                      12.1. For the purposes of this Section 12, “<b>Project Liberty</b>”, “<b>we</b>”, or “<b>us</b>”
                      shall include Project Liberty, its parent company, subsidiaries, affiliates, investors, agents,
                      and successors and assigns.
                    </li>
                    <li class="px-4 py-4">
                      12.2. TO THE MAXIMUM EXTENT PERMITTED BY LAW, NEITHER PROJECT LIBERTY NOR ITS SERVICE PROVIDERS
                      INVOLVED IN CREATING, PRODUCING, OR DELIVERING THE SERVICES WILL BE LIABLE FOR ANY INCIDENTAL,
                      SPECIAL, EXEMPLARY OR CONSEQUENTIAL DAMAGES, OR DAMAGES FOR LOST PROFITS, LOST REVENUES, LOST
                      SAVINGS, LOST BUSINESS OPPORTUNITY, LOSS OF DATA OR GOODWILL, SERVICE INTERRUPTION, COMPUTER
                      DAMAGE OR SYSTEM FAILURE OR THE COST OF SUBSTITUTE SERVICES OF ANY KIND ARISING OUT OF OR IN
                      CONNECTION WITH THESE TERMS OR FROM THE USE OF OR INABILITY TO USE THE SERVICES, WHETHER BASED ON
                      WARRANTY, CONTRACT, TORT (INCLUDING NEGLIGENCE), PRODUCT LIABILITY OR ANY OTHER LEGAL THEORY, AND
                      WHETHER OR NOT PROJECT LIBERTY OR ITS SERVICE PROVIDERS HAS BEEN INFORMED OF THE POSSIBILITY OF
                      SUCH DAMAGE, EVEN IF A LIMITED REMEDY SET FORTH HEREIN IS FOUND TO HAVE FAILED OF ITS ESSENTIAL
                      PURPOSE. TO THE MAXIMUM EXTENT PERMITTED BY THE LAW OF THE APPLICABLE JURISDICTION, IN NO EVENT
                      WILL PROJECT LIBERTY'S TOTAL LIABILITY ARISING OUT OF OR IN CONNECTION WITH THESE TERMS OR FROM
                      THE USE OF OR INABILITY TO USE THE SERVICES EXCEED THE AMOUNTS YOU HAVE PAID OR ARE PAYABLE BY YOU
                      TO PROJECT LIBERTY FOR USE OF THE SERVICES OR ONE HUNDRED DOLLARS ($100), IF YOU HAVE NOT HAD ANY
                      PAYMENT OBLIGATIONS TO PROJECT LIBERTY, AS APPLICABLE.
                    </li>
                    <li class="px-4 pt-4">
                      12.3. THE EXCLUSIONS AND LIMITATIONS OF DAMAGES SET FORTH ABOVE ARE FUNDAMENTAL ELEMENTS OF THE
                      PROJECT LIBERTY'S OFFER TO PROVIDE SERVICES.
                    </li>
                  </ul>
                </li>
                <li id="limited-license">
                  <h5 class="py-1">13. Limited License</h5>
                  <p>
                    Subject to your compliance with these Terms, we grant you limited, non-exclusive, revocable
                    permission to make use of the Services (“<b>Access</b>”). This Access shall remain in effect until
                    and unless terminated by you or us. You understand that this Access is personal to you and you
                    promise and agree that you will not attempt to redistribute, sublicense, or otherwise attempt to
                    transfer Access or the Services, except as expressly provided under these Terms.
                  </p>
                  <p>
                    We, and our licensors, retain ownership of all copies of the Services, or any part thereof, even
                    after installation on your personal computers, mobile devices, tablets, wearable devices, speakers
                    and/or other devices.
                  </p>
                  <p>
                    All Project Liberty trademarks, service marks, trade names, logos, domain names, and any other
                    features of the Project Liberty brand (“<b>Project Liberty Brand Features</b>”) are the sole
                    property of Project Liberty or its licensors. The Terms do not grant you any rights to use any
                    Project Liberty Brand Features whether for commercial or non- commercial use.
                  </p>
                  <p>
                    You agree to abide by our user guidelines and not to use the Services or any part thereof in any
                    manner not expressly permitted by the Terms. Except for the rights expressly granted to you in the
                    Terms, Project Liberty grants no right, title, or interest to you in the Services.
                  </p>
                  <p>
                    Third party software (for example, open source software libraries) included in the Services are made
                    available to you under the relevant third party software library's license terms. Notwithstanding
                    anything herein to the contrary, nothing in the Terms entitles you to copy, modify, fork, merge,
                    combine with another program or create a derivative work of the Services.
                  </p>
                </li>
                <li id="links">
                  <h5 class="py-1">14. Links</h5>
                  The Services provide, or third parties may provide, links to other sites, applications or resources. Because
                  Project Liberty has no control over such sites, applications and resources, you acknowledge and agree that
                  Project Liberty is not responsible for the availability of such external sites, applications or resources,
                  and does not endorse and is not responsible or liable for any content, advertising, products or other materials
                  on or available from such sites or resources. You further acknowledge and agree that Project Liberty shall
                  not be responsible or liable, directly or indirectly, for any damage or loss caused or alleged to be caused
                  by or in connection with use of or reliance on any such content, goods or services available on or through
                  any such site or resource.
                </li>
                <li id="termination-and-suspension">
                  <h5 class="py-1">15. Termination and Suspension</h5>
                  We may suspend or terminate your access to and use of the Services, including suspending access to or terminating
                  your account, at our sole discretion, at any time and without notice to you. You may cancel your account
                  at any time by sending us an email at<a href="mailto:support@frequencyaccess.com" class="underline"
                    >support@frequencyaccess.com</a
                  >. Upon any termination, discontinuation or cancellation of the Services or your account, the
                  following Sections will survive: 7, 8, 9, 10, 11, 12, 13, 16, 17, 18, 19, and this sentence of Section
                  15.
                </li>
                <li id="no-third-party-beneficiaries">
                  <h5 class="py-1">16. No Third Party Beneficiaries</h5>
                  You agree that, except as otherwise expressly provided in these Terms, there shall be no third party beneficiaries
                  to the Terms.
                </li>
                <li id="governing-law-and-forum-choice">
                  <h5 class="py-1">17. Governing Law and Forum Choice</h5>
                  These Terms and any action related thereto will be governed by the Federal Arbitration Act, federal arbitration
                  law, and the laws of the State of California, without regard to its conflict of laws provisions. Except
                  as otherwise expressly set forth in Section 18 “<b>Dispute Resolution</b>,” the exclusive jurisdiction
                  for all Disputes (defined below) that you and Project Liberty are not required to arbitrate will be
                  the state and federal courts located in Los Angeles, California, and you and Project Liberty each
                  waive any objection to jurisdiction and venue in such courts.
                </li>
                <li id="dispute-resolution">
                  <h5 class="py-1">18. Dispute Resolution</h5>
                  <ul class="*:px-4 *:py-2">
                    <li>
                      18.1. <u>Informal Dispute Resolution</u>. You and Project Liberty must first attempt to resolve
                      any dispute, claim, or controversy arising out of or relating to these Terms, or the breach,
                      termination, enforcement, interpretation, or validity thereof, or the use of the Services
                      (collectively, “<b>Disputes</b>”) informally. Accordingly, neither you nor Project Liberty may
                      start a formal arbitration proceeding for at least sixty (60) days after one party notifies the
                      other party of a claim in writing. As part of this informal resolution process, you must deliver
                      written notices via hand or first-class mail to us at Project Liberty LLC, C/O Corporation Trust
                      Center, 1209 Orange Street, Wilmington, Delaware 19801. Notwithstanding the foregoing, we each
                      retain the right to seek injunctive or other equitable relief from a court to prevent (or enjoin)
                      the infringement or misappropriation of our intellectual property rights at any time.
                    </li>
                    <li>
                      18.2. <u>Mandatory Arbitration of Disputes</u>. We each agree that any Dispute will be resolved
                      solely by binding, individual arbitration and not in a class, representative, or consolidated
                      action or proceeding. You and Project Liberty agree that the U.S. Federal Arbitration Act governs
                      the interpretation and enforcement of these Terms, and that you and Project Liberty are each
                      waiving the right to a trial by jury or to participate in a class action. This arbitration
                      provision shall survive termination of these Terms.
                    </li>
                    <li>
                      18.3. <u>Exceptions</u>. As limited exceptions to Section 18.2 above: (i) we both may seek to
                      resolve a Dispute in small claims court if it qualifies; and (ii) we each retain the right to seek
                      injunctive or other equitable relief from a court to prevent (or enjoin) the infringement or
                      misappropriation of our intellectual property rights.
                    </li>
                    <li>
                      <p>
                        18.4. <u>Conducting Arbitration and Arbitration Rules</u>. The arbitration will be conducted by
                        the American Arbitration Association (“<b>AAA</b>”) under its Consumer Arbitration Rules (the
                        “AAA Rules”) then in effect, except as modified by these Terms. The AAA Rules are available at
                        <a href="http://www.adr.org" class="underline" target="_blank">www.adr.org</a> or by calling
                        <a href="tel:800-778-7879" class="underline">1-800-778-7879</a>. A party who wishes to start
                        arbitration must submit a written Demand for Arbitration to AAA and give notice to the other
                        party as specified in the AAA Rules. The AAA provides a form Demand for Arbitration at
                        <a href="http://www.adr.org" class="underline" target="_blank">www.adr.org</a>
                      </p>
                      <p>
                        Any arbitration hearings will take place in the county (or parish) where you live, unless we
                        both agree to a different location. The parties agree that the arbitrator shall have exclusive
                        authority to decide all issues relating to the interpretation, applicability, enforceability and
                        scope of this arbitration agreement.
                      </p>
                    </li>
                    <li>
                      18.5. <u>Arbitration Costs</u>. Payment of all filing, administration and arbitrator fees will be
                      governed by the AAA Rules, and we won't seek to recover the administration and arbitrator fees we
                      are responsible for paying, unless the arbitrator finds your Dispute frivolous. If we prevail in
                      arbitration we'll pay all of our attorneys' fees and costs and won't seek to recover them from
                      you. If you prevail in arbitration you will be entitled to an award of attorneys' fees and
                      expenses to the extent provided under applicable law.
                    </li>
                    <li>
                      18.6. <u>Injunctive and Declaratory Relief</u>. Except as provided in Section 18.3 above, the
                      arbitrator shall determine all issues of liability on the merits of any claim asserted by either
                      party and may award declaratory or injunctive relief only in favor of the individual party seeking
                      relief and only to the extent necessary to provide relief warranted by that party's individual
                      claim. To the extent that you or we prevail on a claim and seek public injunctive relief (that is,
                      injunctive relief that has the primary purpose and effect of prohibiting unlawful acts that
                      threaten future injury to the public), the entitlement to and extent of such relief must be
                      litigated in a civil court of competent jurisdiction and not in arbitration. The parties agree
                      that litigation of any issues of public injunctive relief shall be stayed pending the outcome of
                      the merits of any individual claims in arbitration.
                    </li>
                    <li>
                      18.7. <u>Class Action Waiver</u>. YOU AND PROJECT LIBERTY AGREE THAT EACH MAY BRING CLAIMS AGAINST
                      THE OTHER ONLY IN YOUR OR ITS INDIVIDUAL CAPACITY, AND NOT AS A PLAINTIFF OR CLASS MEMBER IN ANY
                      PURPORTED CLASS OR REPRESENTATIVE PROCEEDING. Further, if the parties' Dispute is resolved through
                      arbitration, the arbitrator may not consolidate another person's claims with your claims, and may
                      not otherwise preside over any form of a representative or class proceeding. If this specific
                      provision is found to be unenforceable, then the entirety of this Dispute Resolution section shall
                      be null and void.
                    </li>
                    <li>
                      18.8. <u>Severability</u>. With the exception of any of the provisions in Section 18.7 of these
                      Terms (“<b>Class Action Waiver</b>”), if an arbitrator or court of competent jurisdiction decides
                      that any part of these Terms is invalid or unenforceable, the other parts of these Terms will
                      still apply.
                    </li>
                  </ul>
                </li>
                <li id="general-terms">
                  <h5 class="py-1">19. General Terms</h5>
                  <ul class="*:px-4 *:py-2">
                    <li>
                      19.1. <u>Reservation of Rights</u>. Project Liberty and its licensors exclusively own all right,
                      title and interest in and to the Services, including all associated intellectual property rights.
                      You acknowledge that the Services are protected by copyright, trademark, and other laws of the
                      United States and foreign countries. You agree not to remove, alter or obscure any copyright,
                      trademark, service mark or other proprietary rights notices incorporated in or accompanying the
                      Services.
                    </li>
                    <li>
                      19.2. <u>Entire Agreement</u>. These Terms constitute the entire and exclusive understanding and
                      agreement between Project Liberty and you regarding the Services, and these Terms supersede and
                      replace all prior oral or written understandings or agreements between Project Liberty and you
                      regarding the Services. If any provision of these Terms is held invalid or unenforceable by an
                      arbitrator or a court of competent jurisdiction, that provision will be enforced to the maximum
                      extent permissible and the other provisions of these Terms will remain in full force and effect.
                      You may not assign or transfer these Terms, by operation of law or otherwise, without Project
                      Liberty's prior written consent. Any attempt by you to assign or transfer these Terms, without
                      such consent, will be null. Project Liberty may freely assign or transfer these Terms without
                      restriction. Subject to the foregoing, these Terms will bind and inure to the benefit of the
                      parties, their successors and permitted assigns.
                    </li>
                    <li>
                      19.3. <u>Notices</u>. Any notices or other communications provided by Project Liberty under these
                      Terms will be given: (i) via email; or (ii) by posting to the Services. For notices made by email,
                      the date of receipt will be deemed the date on which such notice is transmitted.
                    </li>
                    <li>
                      19.4. <u>Waiver of Rights</u>. Project Liberty's failure to enforce any right or provision of
                      these Terms will not be considered a waiver of such right or provision. The waiver of any such
                      right or provision will be effective only if in writing and signed by a duly authorized
                      representative of Project Liberty. Except as expressly set forth in these Terms, the exercise by
                      either party of any of its remedies under these Terms will be without prejudice to its other
                      remedies under these Terms or otherwise.
                    </li>
                  </ul>
                </li>
                <li id="contact-information">
                  <h5 class="py-1">20. Contact Information</h5>
                  <p>
                    If you have any questions about these Terms or the Services, please contact Project Liberty at
                    <a href="mailto:support@frequencyaccess.com" class="underline">support@frequencyaccess.com</a>.
                  </p>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </Tabs.Content>
      <Tabs.Content value="developerTerms">
        <div id="developerTerms">
          <div class="py-6">
            <div>
              <p><b>Last Updated: April 21, 2025</b></p>
              <br />
              <div>
                <ul class="*:pb-4 *:pl-4 *:underline">
                  <li>
                    <a href="#account-and-registration">1. Account and Registration</a>
                  </li>
                  <li>
                    <a href="#using-our-services">2. Using Our Services</a>
                  </li>
                  <li>
                    <a href="#your-clients">3. Your Clients</a>
                  </li>
                  <li>
                    <a href="#prohibitions-and-confidentiality">4. Prohibitions and Confidentiality</a>
                  </li>
                  <li>
                    <a href="#brand-features">5. Brand Features; Attribution</a>
                  </li>
                  <li>
                    <a href="#licenses">6. Licenses; IP Ownership; Privacy</a>
                  </li>
                  <li>
                    <a href="#termination">7. Termination</a>
                  </li>
                  <li>
                    <a href="#liability">8. Liability for our Services</a>
                  </li>
                  <li>
                    <a href="#general-provisions">9. General Provisions</a>
                  </li>
                  <li>
                    <a href="#appendix">APPENDIX A DATA PROTECTION AGREEMENT FOR DEVELOPERS</a>
                  </li>
                </ul>
                <p>
                  Thank you for using Frequency Access, other developer services, and associated software (collectively,
                  "Services"). By accessing or using the Services, you (“Developer” or “you”) are agreeing to the terms
                  below and any applicable policies and guidelines (collectively, “Terms”). You agree to comply with the
                  Terms and that the Terms control your relationship with us. If you use other Frequency-related
                  products or services, then their terms also apply. Under the Terms, "Project Liberty" means Project
                  Liberty LLC, with offices at Corporation Trust Center, 1209 Orange Street, Wilmington, DE 19801 (we
                  may refer to Project Liberty as "we", "our", or "us").
                </p>
              </div>
            </div>
          </div>
          <div class="-pt-0.5">
            <div>
              <ul class="*:pb-6">
                <li id="account-and-registration">
                  <h5 class="py-1">1. Account and Registration</h5>
                  <ul>
                    <li class="px-4 pt-2">
                      <p>
                        1.1. <u>Accepting the Terms.</u> You may not use the Services and may not accept the Terms if (a)
                        you are not of legal age to form a binding contract with us, or (b) you are a person barred from
                        using or receiving the Services under the applicable laws of the United States or other countries
                        (including the country in which you reside or use the Services). If you are using the Services on
                        behalf of an entity, you represent and warrant that you have authority to bind that entity to the
                        Terms and by accepting the Terms, you are doing so on behalf of that entity (and "you" refers to
                        that entity).
                      </p>
                    </li>
                    <li class="px-4 py-4">
                      <p>
                        1.2. <u>Registration.</u> To access certain Services, you may be required to provide information
                        (such as identification or contact details) as part of the registration process for (or as part of
                        your continued use of) the Services. Any registration information you give us will always be accurate
                        and up to date and you will inform us promptly of any updates.
                      </p>
                    </li>
                    <li class="px-4">
                      <p>
                        1.3. <u>Subsidiaries and Affiliates.</u> Project Liberty may have subsidiaries and affiliated legal
                        entities in various locations. These companies may provide Services to you on behalf of Project Liberty
                        and the Terms will also govern your relationship with these entities.
                      </p>
                    </li>
                  </ul>
                </li>
                <li id="using-our-services">
                  <h5 class="py-1">2. Using Our Services</h5>
                  <ul class="*:px-4 *:py-2">
                    <li>
                      <p>
                        2.1. <u>Compliance with Law and Other Project Liberty Terms of Service.</u> You will comply with
                        all applicable laws, regulations, and third-party rights (including, without limitation, laws regarding
                        the import or export of data or software, privacy, and local laws). You will not use the Services
                        to encourage or promote illegal activity or violation of third-party rights. You will not violate
                        any other terms of service with Project Liberty, Frequency, or their affiliates. Additionally, you
                        will require your end users to comply with (and not knowingly enable them to violate) applicable
                        law, regulation, and the Terms.
                      </p>
                    </li>
                    <li>
                      <p>
                        2.2. <u>Acknowledgment of Development Status of the Services.</u> You acknowledge and agree that:
                        (a) the Services are still under development; (b) the Services are not in final form and may not
                        operate properly or be fully functional; (c) the Services may contain errors, design flaws or other
                        problems; (d) it may not be possible to make the Services fully functional; (e) the information obtained
                        using the Services may not be accurate; (f) use of the Services may result in unexpected results,
                        loss of data or communications, project delays or other unpredictable damage or loss, and it is your
                        responsibility to back up and restore any data that you store in or transmit through the Services;
                        (g) Project Liberty is under no obligation to release a commercial version of the Services; and (h)
                        Project Liberty has the right unilaterally to abandon development of the Services, at any time and
                        without any obligation or liability to you.
                      </p>
                    </li>
                    <li>
                      <p>
                        2.3. <u>Permitted Access.</u> You will only access (or attempt to access) the Services by the means
                        described in the documentation for the Service. If Project Liberty assigns you developer credentials
                        (e.g., client IDs), you must use them with the applicable Services. You will not misrepresent or
                        mask either your identity when using the Services or developer accounts.
                      </p>
                    </li>
                    <li>
                      <p>
                        2.4. <u>Costs.</u> Each party shall pay its own fees, costs, and expenses (and those of its agents,
                        independent contractors, consultants, and affiliates) in connection with these Terms and Services.
                      </p>
                    </li>
                    <li>
                      <p>
                        2.5. <u>Service Limitations.</u> Project Liberty may set and enforce limits on your use of the Services
                        (e.g., limiting the number of logon requests that you may make or the number of users you may serve),
                        in our sole discretion. You agree to, and will not attempt to circumvent, such limitations. If you
                        would like to use any Services beyond these limits, you must obtain our express consent (and Project
                        Liberty may decline or condition acceptance on additional terms and/or charges).
                      </p>
                    </li>
                    <li>
                      <p>
                        2.6. <u>Communication and Feedback.</u> We may send you certain communications in connection with
                        the Services. Please contact us to opt out. If you provide feedback or suggestions about our Services,
                        then we may use such information without obligation to you.
                      </p>
                    </li>
                    <li>
                      <p>
                        2.7. <u>Non-Exclusivity.</u> The Terms are non-exclusive. You acknowledge that Project Liberty and/or
                        its affiliates may develop products or services that may compete with any other products or services,
                        including yours.
                      </p>
                    </li>
                    <li>
                      <p>
                        2.8. <u>Project Liberty Controller-Controller Data Protection Terms.</u> To the extent Developer
                        processes any personal data on behalf of Project Liberty, or as otherwise required by data protection
                        laws applicable to the parties' processing of personal data under these Terms, the parties agree
                        to Project Liberty’s Data Protection Agreement for Developers in Appendix A below.
                      </p>
                    </li>
                  </ul>
                </li>
                <li id="your-clients">
                  <h5 class="py-1">3. Your Clients</h5>
                  <ul class="*:px-4 *:py-2">
                    <li>
                      <p>
                        3.1. <u>Security.</u> You will use commercially reasonable efforts to implement the Services and
                        protect user information, including personal data, from unauthorized access or use and will promptly
                        report to your users any unauthorized access or use of such information to the extent required by
                        applicable law.
                      </p>
                    </li>
                    <li>
                      <p>
                        3.2. <u>Ownership.</u> Project Liberty does not acquire ownership in your website or services, and
                        by using our Services, you do not acquire ownership of or any intellectual property or rights in
                        our Services.
                      </p>
                    </li>
                    <li>
                      <p>
                        3.3. <u>User Privacy.</u> You will comply with (1) all applicable privacy laws and regulations, including
                        those applying to personal data; and (2) our Privacy Policy. You will provide and adhere to a privacy
                        policy for your website or services that describes what information you collect and how you use and
                        share it.
                      </p>
                    </li>
                    <li>
                      <p>
                        3.4. <u>Customer Service.</u> You agree to be responsible for providing your clients with commercially
                        reasonable customer service and technical support measures (“Customer Service”) regarding the Services
                        (or your implementation of thereof). Nothing in these Terms shall obligate Project Liberty to provide
                        any amount or level of Customer Service, whether to Developers or your clients. If Project Liberty
                        or its affiliates choose to provide Customer Service, they may, in their sole discretion,  limit
                        or discontinue Customer Service at any time, apply a fee structure, and control or change the relevant
                        channels and procedures.
                      </p>
                    </li>
                  </ul>
                </li>
                <li id="prohibitions-and-confidentiality">
                  <h5 class="py-1">4. Prohibitions and Confidentiality</h5>
                  <ul>
                    <li class="px-4 pt-2">
                      <p>
                        4.1. <u>Prohibitions.</u> When using the Services, you may not (or allow those acting on your behalf
                        to):
                      </p>
                      <ul class="list-disc *:ml-8">
                        <li class="pt-2">
                          <p>
                            Sublicense the Services for use by a third party. Consequently, you will not create a
                            service, code, or widget that functions substantially the same as the Services and offer it
                            for use by third parties.
                          </p>
                        </li>
                        <li>
                          <p>
                            Perform an action with the intent of introducing to Project Liberty products and services
                            any viruses, worms, defects, Trojan horses, malware, or any items of a destructive nature.
                          </p>
                        </li>
                        <li>
                          <p>
                            Defame, abuse, harass, stalk, or threaten others, promote unlawful gambling, or disruptive
                            advertisements.
                          </p>
                        </li>
                        <li>
                          <p>
                            Interfere with or disrupt the Services or the servers or networks providing the Services.
                          </p>
                        </li>
                        <li>
                          Reverse engineer or attempt to extract the source code from any Services or any related
                          software, except to the extent that this restriction is expressly prohibited by applicable
                          law.
                        </li>
                        <li>
                          Use the Services for any activities where the use or failure of the Services would violate
                          applicable law or regulations or could lead to death, personal injury, or environmental
                          damage.
                        </li>
                        <li>
                          Use the Services to process or store any data that is subject to the International Traffic in
                          Arms Regulations maintained by the U.S. Department of State.
                        </li>
                        <li>
                          Remove, obscure, or alter any Project Liberty terms of service or any links to or notices
                          thereto.
                        </li>
                        <li>
                          Use the Services for any purpose or in any manner involving transmitting protected health
                          information  unless you have received prior written consent from Project Liberty.
                        </li>
                      </ul>
                    </li>
                    <li class="px-4 pt-4">
                      4.2. <u>Credentials and Confidential Matters.</u> When applicable, developer credentials (such as passwords,
                      keys, and client IDs) are intended to be used by you alone. You will keep your credentials confidential.
                      Moreover, our communications to you and our Services may contain Project Liberty confidential information.
                      Project Liberty confidential information includes any materials, communications, and information that
                      are marked confidential or that would normally be considered confidential under the circumstances.
                      If you receive any such information, then you will not disclose it to any third party without Project
                      Liberty's prior written consent. Project Liberty confidential information does not include information
                      that you independently developed, that was rightfully given to you by a third party without confidentiality
                      obligation, or that becomes public through no fault of your own. You may disclose Project Liberty confidential
                      information when compelled to do so by law if you provide us reasonable prior notice, unless a court
                      orders that we not receive notice.
                    </li>
                  </ul>
                </li>
                <li id="brand-features">
                  <h5 class="py-1">5. Brand Features; Attribution</h5>
                  <ul class="*:px-4 *:py-2">
                    <li>
                      5.1. <u>Brand Features.</u> "Brand Features" is defined as the trade names, trademarks, service marks,
                      logos, domain names, and other distinctive brand features of each party. Except where expressly stated,
                      the Terms do not grant either party any right, title, or interest in or to the other party's Brand
                      Features. All use by you of Project Liberty's Brand Features (including any goodwill associated therewith)
                      will inure to the benefit of Project Liberty.
                    </li>
                    <li>
                      5.2. <u>Attribution.</u> You agree to display any attribution(s) requested by Project Liberty or described
                      in documentation for the Services. Project Liberty hereby grants to you a nontransferable, non-sublicensable,
                      nonexclusive license (while the Terms are in effect) to display Project Liberty's Brand Features for
                      the purpose of promoting or advertising that you use the Services. You must only use Project Liberty
                      Brand’s Features in accordance with the Terms and for the purpose of fulfilling your obligations under
                      this Section. You understand and agree that Project Liberty has the sole discretion to determine whether
                      your attribution(s) and use of Project Liberty's Brand Features comply with these requirements.
                    </li>
                    <li>
                      5.3. <u>Publicity.</u> You will not make any statement regarding your use of the Services which suggests
                      partnership with, sponsorship by, or endorsement by Project Liberty without Project Liberty's prior
                      written approval.
                    </li>
                    <li>
                      5.4. <u>Promotional and Marketing Use.</u> In the course of promoting, marketing, or demonstrating
                      the Services you are using and the associated Project Liberty products, Project Liberty may produce
                      and distribute incidental depictions, including screenshots, video, or other content from your use
                      of the Services, and may use your company or product name. You grant us all necessary rights for the
                      above purposes.
                    </li>
                  </ul>
                </li>
                <li id="licenses">
                  <h5 class="py-1">6. Licenses; IP Ownership; Privacy</h5>
                  <ul class="*:px-4 *:py-2">
                    <li>
                      6.1. <u>License to Developer.</u> Subject to and conditional upon your compliance at all times with
                      these Terms, Project Liberty grants to you a limited, non-exclusive, non-transferable, non-sublicensable,
                      revocable right during the Term to do the following: (i) use software, API(s), widgets, or interoperability
                      features made available by Project Liberty through the Services to develop and distribute applications
                      and service(s) consistent with these Terms; and (ii) use Project Liberty’s Brand Features consistent
                      with Section 5. You agree that, except for the limited license above, these Terms do not grant you
                      any rights to Project Liberty’s Brand Features and that all goodwill generated through your use of
                      such marks is to the sole benefit of Project Liberty.
                    </li>
                    <li>
                      6.2. <u>No Grant of Proprietary Rights.</u> Except as expressly granted in these Terms, neither party
                      grants the other party any intellectual property rights or other proprietary rights. As between you
                      and Project Liberty, Project Liberty, its affiliates, and its applicable licensors retain all intellectual
                      property rights (including all patent, trademark, copyright, trade secret, and other proprietary rights)
                      in and to the Services (including its documentation and specifications) and any derivative works thereof.
                    </li>
                    <li>
                      6.3. <u>Third Party Software.</u> The Services may include open source software or third party software.
                      Any such software is made available to you under the terms of the applicable licenses.
                    </li>
                    <li>
                      6.4. <u>Intellectual property.</u> The Services and any of Project Liberty’s Brand Features (our “Intellectual
                      Property”) are the property of Project Liberty or Project Liberty’s licensors and protected by intellectual
                      property rights. You do not have the right to use the Intellectual Property in any manner not covered
                      by these Terms. Nothing in these Terms shall be construed to convey (and by virtue of these Terms you
                      will not acquire) any ownership interest in the Intellectual Property. You will not contest (or assist
                      others in contesting) the validity, enforceability, ownership, or title of any Intellectual Property.
                      You agree not to attempt to use or register any trademark or domain name that includes the word “Project
                      Liberty,” any other Project Liberty trademark, or any name that is confusingly similar to any of them.
                      Further, you may not remove or alter any copyright, copyright protection technology, trademark, or
                      other intellectual property notice contained in or provided through Project Liberty’s Intellectual
                      Property.
                    </li>
                    <li>
                      6.5. <u>Privacy.</u> By using our Services and sharing or submitting any data to Project Liberty (as
                      applicable), you agree that we may use submitted information in accordance with our privacy policies.
                    </li>
                  </ul>
                </li>
                <li id="termination">
                  <h5 class="py-1">7. Termination</h5>
                  <ul>
                    <li class="px-4 pt-2">
                      7.1. <u>Termination.</u> You may stop using our Services at any time with or without notice. Further,
                      if you want to terminate the Terms, you must provide Project Liberty with prior written notice and
                      upon termination, cease your use of the applicable Services. Project Liberty reserves the right to
                      terminate the Terms with you or discontinue the Services or any portion or feature or your access thereto
                      for any reason and at any time without liability or other obligation to you.
                    </li>
                    <li class="px-4 py-4">
                      7.2. <u>Your Obligations Post-Termination.</u> Upon any termination of the Terms or discontinuation
                      of your access to the Service, you will immediately stop using the Services, and cease all use of Project
                      Liberty’s Brand Features. Project Liberty may independently communicate with any account owner whose
                      account(s) are associated with you to provide notice of the termination.
                    </li>
                    <li class="px-4">
                      7.3. <u>Surviving Provisions.</u> When the Terms come to an end, those terms that by their nature are
                      intended to continue indefinitely will continue to apply, including but not limited to: Sections 4b,
                      7, 8, and 9.
                    </li>
                  </ul>
                </li>
                <li id="liability">
                  <h5 class="py-1">8. Liability for our Services</h5>
                  <ul>
                    <li class="px-4 pt-2">
                      8.1. <u>WARRANTIES.</u> EXCEPT AS EXPRESSLY SET OUT IN THE TERMS, NEITHER PROJECT LIBERTY NOR ITS AFFILIATES
                      MAKE ANY SPECIFIC PROMISES ABOUT THE SERVICES. FOR EXAMPLE, WE DO NOT MAKE ANY COMMITMENTS ABOUT THE
                      SPECIFIC FUNCTIONS OF THE SERVICES, OR THEIR RELIABILITY, AVAILABILITY, OR ABILITY TO MEET YOUR NEEDS.
                      WE PROVIDE THE SERVICES "AS IS". EXCEPT AS EXPRESSLY PROVIDED FOR IN THE TERMS, TO THE EXTENT PERMITTED
                      BY LAW, WE EXCLUDE ALL WARRANTIES, GUARANTEES, CONDITIONS, REPRESENTATIONS, AND UNDERTAKINGS.
                    </li>
                    <li class="px-4 py-4">
                      <p>
                        8.2. <u>LIMITATION OF LIABILITY.</u> WHEN PERMITTED BY LAW, PROJECT LIBERTY AND ITS AFFILIATES, WILL
                        NOT BE RESPONSIBLE FOR LOST PROFITS, REVENUES, OR DATA; FINANCIAL LOSSES; OR INDIRECT, SPECIAL, CONSEQUENTIAL,
                        EXEMPLARY, OR PUNITIVE DAMAGES. TO THE EXTENT PERMITTED BY LAW, THE TOTAL LIABILITY OF PROJECT LIBERTY,
                        AND ITS SUPPLIERS AND DISTRIBUTORS, FOR ANY CLAIM UNDER THE TERMS, INCLUDING FOR ANY IMPLIED WARRANTIES,
                        IS LIMITED TO THE AMOUNT YOU PAID US TO USE THE APPLICABLE SERVICES (OR, IF WE CHOOSE, TO SUPPLYING
                        YOU THE SERVICES AGAIN) DURING THE SIX (6) MONTHS PRIOR TO THE EVENT GIVING RISE TO THE LIABILITY.
                        IN ALL CASES, PROJECT LIBERTY, AND ITS SUPPLIERS AND DISTRIBUTORS, WILL NOT BE LIABLE FOR ANY EXPENSE,
                        LOSS, OR DAMAGE THAT IS NOT REASONABLY FORESEEABLE.
                      </p>
                      <p>
                        PROJECT LIBERTY IS NOT RESPONSIBLE FOR, AND CANNOT UNILATERALLY CREATE, MODIFY OR DELETE YOUR
                        MESSAGING ACCOUNT(S) (MSA), HANDLE, AND OTHER INFORMATION ON SUPPORTED NETWORKS OR RELEVANT
                        BLOCKCHAIN(S). THE SERVICES ARE NOT DESIGNED TO, AND YOU MAY NOT USE THE SERVICES TO, RECEIVE,
                        HOLD, TRANSMIT, OR TRANSFER CRYPTOCURRENCY OR OTHER CURRENCIES OR MONETARY OR FINANCIAL ASSETS.
                        THE SERVICES AS OFFERED BY PROJECT LIBERTY ARE ONLY IN CONNECTION WITH NON-FINANCIAL SOCIAL
                        INTERACTION WITHIN A SOCIAL NETWORK.
                      </p>
                    </li>
                    <li class="px-4">
                      8.3. <u>Indemnification.</u> You will defend and indemnify Project Liberty, and its affiliates,
                      directors, officers, employees, and users, against all liabilities, damages, losses, costs, fees
                      (including legal fees), and expenses relating to any allegation or third-party legal proceeding to
                      the extent arising from:
                      <ul class="list-disc *:ml-8">
                        <li class="pt-2">your misuse or your end user's misuse of the Services;</li>
                        <li>your violation or your end user's violation of the Terms; or</li>
                        <li>
                          any content or data routed into or used with the Services by you, your agents, or your end
                          users.
                        </li>
                      </ul>
                    </li>
                  </ul>
                </li>
                <li id="general-provisions">
                  <h5 class="py-1">9. General Provisions</h5>
                  <ul>
                    <li class="px-4 pt-2">
                      9.1. <u>Modification.</u> We may modify the Terms or any portion to, for example, reflect changes to
                      the law or changes to our Services. You should look at the Terms regularly. We will post notice of
                      modifications to the Terms to this website. If you do not agree to the modified Terms for the Service,
                      you should discontinue your use of the Services. Your continued use of the Services constitutes your
                      acceptance of the modified Terms.
                    </li>
                    <li class="px-4 py-4">
                      9.2. <u>General Legal Terms.</u> The Terms do not create any third-party beneficiary rights or any
                      agency, partnership, or joint venture. Nothing in the Terms will limit either party's ability to seek
                      injunctive relief. We are not liable for failure or delay in performance to the extent caused by circumstances
                      beyond our reasonable control. If you do not comply with the Terms, and Project Liberty does not take
                      action right away, this does not mean that Project Liberty is waiving any rights it has. If it turns
                      out that a particular term is not enforceable, this will not affect any other terms. The Terms are
                      the entire agreement between you and Project Liberty relating to its subject and supersede any prior
                      or contemporaneous agreements on that subject. To contact Project Liberty, please email support@frequencyaccess.com.
                    </li>
                    <li class="px-4">
                      9.3. <u>Arbitration.</u> These Terms shall be governed by and construed in accordance with the laws
                      of New York without regard to the principles of conflict of laws thereof to the extent such laws would
                      apply the laws of (or direct a matter to) another jurisdiction. Any claim or controversy arising out
                      of or relating to these Terms shall be settled by arbitration administered by the American Arbitration
                      Association in accordance with its Commercial Arbitration Rules. The number of arbitrators shall be
                      three (3), one (1) of whom shall be appointed by each of the parties and the third of whom shall be
                      selected by mutual agreement of the co-arbitrators, within 30 days of the selection of the second arbitrator
                      and thereafter by the administering authority, and the place of arbitration shall be New York, NY.
                      The language of the arbitration shall be English. The arbitration award rendered by the arbitrator(s)
                      shall be final and binding on the parties. Judgment on the award may be entered in any court having
                      jurisdiction thereof.
                    </li>
                  </ul>
                </li>
              </ul>
            </div>
          </div>
          <div>
            <div id="appendix">
              <h5 class="py-1 text-center">APPENDIX A</h5>
              <h5 class="py-1 text-center">DATA PROTECTION AGREEMENT FOR DEVELOPERS</h5>
            </div>
            <div>
              <ul>
                <li>
                  <p>
                    This Data Protection Appendix (Appendix) shall apply if you (“you” or “Developer”) access, receive,
                    use, store or otherwise process Project Liberty Personal Data . The parties agree that this Appendix
                    shall be incorporated into, and form part of, the Developer Terms.
                  </p>
                  <ul class="*:px-4 *:py-2">
                    <li>
                      <p>
                        1.1. <u>Definitions and Interpretation.</u> Terms defined in the Developer Terms of Service (“Developer
                        Terms”) shall have the same meaning when used in this Appendix, unless defined differently in this
                        Appendix.
                      </p>
                      <p>
                        For purposes of this Appendix, “Affiliate” shall mean any entity that directly or indirectly
                        controls, is controlled by, or is under common control with a party (or other entities that
                        Project Liberty, in its sole discretion, designates as an affiliate pursuant to a data sharing
                        agreement); “Applicable Laws” shall mean all laws, regulations and regulatory policies,
                        guidelines or industry codes of any competent industry body that are applicable to Project
                        Liberty, Project Liberty Personal Data, the Project Liberty Services, you and the GDPR;
                        “Personal Data” shall mean any data falling within the definition of “personal data” under the
                        General Data Protection Regulation 2016/679 or any replacement legislation, as applicable and
                        including the UK’s Data Protection Act 2018 (“GDPR”); “Project Liberty” shall mean the Project
                        Liberty entity which is party to the Developer Terms; and “Project Liberty Personal Data” shall
                        mean any Personal Data in respect of which Project Liberty or a Project Liberty Affiliate is a
                        data controller, which you process in connection with the Developer Terms.
                      </p>
                      <p>
                        Terms defined in the GDPR, as applicable, shall have the same meaning when used in this
                        Appendix, unless defined differently in this Appendix.
                      </p>
                    </li>
                    <li>
                      <p>
                        1.2. <u>Your Role.</u> You acknowledge that you shall be acting as an independent Data Controller
                        in respect of Project Liberty Personal Data. If circumstances arise where you are acting as a Data
                        Processor on Project Liberty’s (or a Project Liberty Affiliate’s) behalf, you shall promptly, on
                        request by Project Liberty, execute written contractual commitments which meet the requirements of
                        Applicable Laws in relation to Data Processors.
                      </p>
                      <p>
                        You shall adhere to the obligations, requirements and standards set out in the GDPR in respect
                        of any Project Liberty Personal Data, including: (a) the level of security and / or protection
                        for Project Liberty Personal Data; (b) the rights of Project Liberty; and (c) the rights of the
                        Data Subject(s), irrespective of: (i) the location of the Data Subject(s); (ii) the location of
                        the Project Liberty Affiliate that is party to this Developer Terms; and (iii) your location.
                      </p>
                    </li>
                    <li>
                      <p>
                        1.3. <u>Transfer of Personal Data Outside of the European Economic Area (“EEA”) or UK.</u> Before
                        transferring Personal Data from inside EU/EEA/UK to outside the EU/EEA/UK, you shall ensure that
                        such transfer is fully compliant with Applicable Laws. In addition, you shall be solely responsible
                        for procuring that you execute and deliver all such documents and perform all such acts as requested
                        by Project Liberty for the purpose of giving full effect to this paragraph 3.
                      </p>
                      <p>
                        To the extent you are receiving personal data from Project Liberty as a data importer in the
                        EU/EEA/UK, you represent and warrant that with respect to such data, you: (i) shall ensure that
                        an equivalent level of protection as offered by EU law will be provided to EU/EEA Data Subjects
                        in the country you are processing Personal Data, and (ii) shall implement supplemental measures
                        consistent with regulatory guidance (and remain consistent as further regulatory guidance is
                        issued).
                      </p>
                      <p>
                        The EU standard contractual clauses adopted by decision of 4 June 2021 document number
                        C/2021/3972 (module 1, controllers to controllers) (“SCCs”) shall apply to any transfers of
                        Project Liberty Personal Data by Project Liberty under this DPA from the European Union (“EU”)
                        and the European Economic Area (“EEA”) to you where you are in a third country for the purposes
                        of GDPR.
                      </p>
                      <p>
                        You represent and warrant that you can comply with the SCCs and accept the transfer of Project
                        Liberty Personal Data. To the extent you become unable to comply with the SCCs, you shall
                        promptly notify Project Liberty and Project Liberty may terminate the Developer Terms and your
                        access to the Project Liberty Platform in its sole discretion.
                      </p>
                      <p>
                        For the purposes of the SCCs, the parties agree that Project Liberty LLC may constitute the
                        “data exporter” and you would then be the “data importer“ and that:
                      </p>
                      <p>For the purposes of Annex I of the Appendix to the SCCs, the following will apply:</p>
                      <p>
                        a: <u>List of Parties.</u> The names and contact details of the parties shall be as set out in the
                        applicable approval form for the services or their account.
                      </p>
                      <p>b: <u>Description of Transfer.</u></p>
                      <ul class="list-disc *:ml-8">
                        <li class="pt-2">
                          <u>Data Subjects.</u> The Personal Data transferred concern the following categories of Data Subjects:
                          Project Liberty users.
                        </li>
                        <li>
                          <u>Purpose and nature of the transfer(s).</u> The transfer is made for the following purposes:
                          To provide the ability for you to use the Project Liberty Platform in accordance with the Developer
                          Terms.
                        </li>
                        <li>
                          <u>Categories of data.</u> The Personal Data transferred concern the following categories of data:
                          Project Liberty Personal Data (including, but not limited to, emails, names, phone numbers, contact
                          information, and any other personal data submitted by Project Liberty to use the Services).
                        </li>
                        <li>
                          <u>Recipients.</u> The Personal Data transferred may be disclosed only to the following recipients
                          or categories of recipient: Data Processors appointed by you.
                        </li>
                        <li>
                          <u>Sensitive data.</u> The Personal Data transferred concern the following categories of sensitive
                          data: N/A.
                        </li>
                        <li><u>Frequency.</u> Continuous.</li>
                        <li>
                          <u>Period for which data will be retained.</u> As set out in your privacy policy.
                        </li>
                        <li><u>Transfers to (sub) processors.</u> N/A.</li>
                      </ul>
                      <p>
                        C: <u>Competent Supervisory Authority.</u> The relevant competent supervisory authority shall be
                        the Irish Data Protection Commission (DPC).
                      </p>
                      <p>For purposes of Annex II of the Appendix to the SCCs, the following will apply:</p>
                      <p>
                        Data importer shall undertake appropriate technical and organizational security measures to
                        protect personal data against the unauthorized or unlawful processing of personal data and
                        against accidental loss or destruction of, or damage to, personal data. These measures should
                        take into account available encryption technology and the costs of implementing the specific
                        measures and must ensure a level of security appropriate to the harm that might result from a
                        breach of security and the nature of the data to be protected.
                      </p>
                    </li>
                    <li>
                      <p>
                        1.4. <u>Processing of Project Liberty Personal Data</u> You must provide reasonable resources to
                        your employees to enable processing of Project Liberty Personal Data in compliance with the Developer
                        Terms.
                      </p>
                      <p>
                        You shall only process Project Liberty Personal Data as is strictly necessary for the provision
                        of your Services or third-party application.
                      </p>
                      <p>
                        Each of Project Liberty and you shall be solely responsible for its respective processing of
                        Project Liberty Personal Data, including allowing Data Subjects to exercise their legal rights
                        under Applicable Laws. You, to the extent that such a request affects Project Liberty’s
                        processing of Personal Data, shall comply with all such requests in accordance with Applicable
                        Laws.
                      </p>
                      <p>
                        You shall also be responsible for any acts and omissions of any third parties with which you
                        share Project Liberty Personal Data.
                      </p>
                      <p>
                        You shall notify Project Liberty Party immediately if you become aware of, or suspect: (i) any
                        breach of this Appendix; or (ii) a Personal Data breach which is likely to affect or invoke the
                        other party’s obligations under Applicable Laws. The notifying party shall document all Personal
                        Data breaches in accordance with Applicable Laws and fully cooperate with the other party to
                        ensure compliance with Applicable Laws. Each party shall use reasonable endeavors to mitigate
                        any damage suffered by a Data Subject.
                      </p>
                    </li>
                    <li>
                      <p>
                        1.5. <u>Account Linking</u> If your access to Project Liberty Personal Data is provided in connection
                        with linking or otherwise integrating the Data Subject’s Project Liberty-provided account with your
                        Services or third-party application, the following shall apply:
                      </p>
                      <ul class="list-disc *:ml-8">
                        <li class="pt-2">
                          The account linking and / or integration request to Data Subjects shall include all
                          information required to ensure it is compliant with Applicable Laws, including sufficient
                          notice: (i) of which Project Liberty Personal Data you will access and the purpose of such
                          processing; and (ii) that you will be responsible for your processing of such data in
                          accordance with your privacy policy.
                        </li>
                        <li>
                          You shall allow Data Subjects to disconnect their Project Liberty-provided account by
                          providing an easily accessible and usable mechanism to disconnect their Project
                          Liberty-provided account from your Services or third-party application at any time, including
                          clear instructions on how to do so.
                        </li>
                        <li>
                          If a Data Subject disconnects accounts as described in (b) above, or if Project Liberty or a
                          Data Subject requests such disconnection, you shall discontinue accessing the applicable
                          Project Liberty Personal Data and delete the applicable Project Liberty Personal Data in your
                          possession or control within five (5) days.
                        </li>
                      </ul>
                    </li>
                    <li>
                      1.6. <u>Information Security Practices</u> You shall implement and maintain all appropriate technical,
                      administrative and organizational measures required to ensure a level of confidentiality and security
                      appropriate to the risks represented by the processing and the nature of Project Liberty Personal Data,
                      and to prevent unauthorized or unlawful processing of Project Liberty Personal Data, including measures
                      against unauthorized or unlawful processing of Project Liberty Personal Data and against accidental
                      loss, corruption, disclosure or destruction of, or damage to, Project Liberty Personal Data.
                    </li>
                    <li>
                      1.7. <u>Obligation to Provide Information</u> If requested by Project Liberty, you will keep Project
                      Liberty informed of the contact details of your data protection representative. You shall provide Project
                      Liberty with any information that Project Liberty reasonably requires in order for Project Liberty
                      to comply with its obligations under Applicable Laws including to inform Data Subjects about your data
                      processing activities and any data transfer solutions utilized by you.
                    </li>
                    <li>
                      1.8. <u>Non-Sale</u> The exchange of Personal Data hereunder does not constitute a sale, as defined
                      by Applicable Law (including the California Consumer Protection Act). As such, you are prohibited from
                      processing (including retaining, using and disclosing) Personnel Data (i) for any purpose other than
                      performing your obligations under the Developer Terms; or (ii) outside of the direct business relationship
                      with Project Liberty (including for a commercial or internal business purposes). You shall ensure that
                      you and any processor or partner you engage to help perform your obligations under the Developer Terms
                      will not, directly or indirectly, make available Personal Data for any valuable consideration (monetary
                      or otherwise). You hereby certify that you understand the undertakings and restrictions set out in
                      this paragraph 8.
                    </li>
                    <li>
                      1.9. <u>Security Incidents and Notices to Third Parties</u> You agree to notify Project Liberty by
                      sending an email to privacy@projectliberty.io without undue delay (and in any event within twenty-four
                      (24) hours) where you become aware of or reasonably suspects that Project Liberty Personal Data has
                      been or may have been lost, damaged or subject to unauthorized access (a “Security Incident”) and to
                      take reasonable steps to mitigate the impact of any such Security Incident. To the extent Project Liberty
                      seeks the assistance of you, you agree to reasonably cooperate with Project Liberty to enable Project
                      Liberty to comply with its obligations under Applicable Laws.
                    </li>
                    <li>
                      1.10. <u>Governing Law</u> Notwithstanding any other provision in the Developer Terms, this Appendix
                      shall be governed by, and interpreted in accordance with, the laws of New York.
                    </li>
                  </ul>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </Tabs.Content>
    </Tabs.Root>
  {/snippet}
</PageOutline>
<Footer {i18n} />
