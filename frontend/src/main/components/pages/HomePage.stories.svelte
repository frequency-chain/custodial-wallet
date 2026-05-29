<script module lang="ts">
  import { defineMeta } from "@storybook/addon-svelte-csf"
  import HomePage from "./HomePage.svelte"
  import { initI18n } from "$lib/i18n"

  const { Story } = defineMeta({
    title: "Pages/HomePage",
    component: HomePage,
    args: {},
  })

  // ************************************
  // *********** I18N Service ***********
  // ************************************
  const i18n = initI18n({
    "home.splash.title.1": "It's Your identity.",
    "home.splash.title.2": "You should own it.",
    "home.splash.subtitle": "Get a front row seat to the movement that will change the way our digital worlds work.",
    "home.social-web.title": "Now you control the way social networks work",
    "home.social-web.subtitle": "The Social Web",
    "home.social-web.description":
      "The way we connect, communicate, and collaborate is dominated today by mega platforms. The Social Web is a new digital world that puts you in control — and your identity, friends and followers are yours to take with you wherever you go. It's the start of a coalition of apps brought together by the shared belief that people should control the way social works, not companies.",
    "home.providers.title": "Companies in the Frequency Ecosystem",
    "home.terms.title": "Glossary of Terms",
    "home.terms.1.title": "Social Identity",
    "home.terms.1.text":
      "Your social Identity is like an online profile that you use to access the social web and to connect with others. The first step to building your social identity is to claim your unique Universal Handle now.",
    "home.terms.2.title": "Universal Handles",
    "home.terms.2.text":
      "When you create your Social Identity, choose a name that's easy to read and consistent across all social web apps. This will help you find connections easily and make it easier for others to find you.",
    "home.terms.3.title": "Frequency",
    "home.terms.3.text":
      "By leveraging the Frequency blockchain, apps can provide millions of people with the basic right to own their Social Identity and access to a robust Web3 feature set.",
    "home.faq.title": "FAQ",
    "home.faq.1.title": 'What is the "Social Web?"',
    "home.faq.1.text":
      "The Social Web is made up of apps that share the vision that people should control how their digital world works and transparent technology infrastructure that gives people this control. Today's web made it possible for large platforms to extract and pool humanity's data as a proprietary corporate asset, used to control nearly every aspect of our digital experience. The Social Web places people and human connection at the forefront, making it possible for people to take control of how their digital world works.",
    "home.faq.2.title": "Why do I need a Frequency Access Account?",
    "home.faq.2.text":
      "In order to interact with the applications on Frequency you need to have an account. A Frequency Access Account allows you to quickly and easily create an account and choose a Universal Handle.",
    "home.faq.3.title": "What is my Universal Handle?",
    "home.faq.3.text":
      "Your user Universal Handle is the username that others can use to find and connect with you on Frequency.",
    "home.faq.4.title": "How do I create my Universal Handle?",
    "home.faq.4.text.1":
      "As part of the account creation process on Frequency Access, you will be asked to create your Universal Handle. You will first enter the name by which you will be known (your Handle Base). We ask that you keep your choice of base handle appropriate. (If in doubt, you can check the",
    "home.faq.4.text.terms": "terms of service",
    "home.faq.4.text.2":
      ".) Also, keep in mind that some words are reserved by Frequency for administrative purposes. After you've selected your base handle, Frequency will add a few numbers (your Handle Suffix) to your Handle Base which will ensure there are no duplicate names on Frequency.",
    "home.faq.5.title": "What is a Provider?",
    "home.faq.5.text.1":
      "A Provider is a person, entity or application that can do tasks on Frequency on your behalf. This allows you to work with Frequency without having to know anything about decentralized technology or blockchain.",
    "home.faq.5.text.2":
      "Once you have created a Frequency Access Account, you can select a Provider and delegate permissions to them. This defines which types of tasks you would like them to do on your behalf. These tasks might include managing your security keys, creating an MSA, accessing your relationship data (social graph), sending and receiving messages, and more. You can revoke this delegation at any time and for any reason. You are in control!",
    "home.faq.6.title": "What is an MSA?",
    "home.faq.6.text":
      "Your Message Source Account (MSA) is unique to Frequency. It allows you to store, send and receive messages and is identified by a number called an MSA Id that is permanently assigned to you at account creation.",
    "home.faq.7.title": "How do I contact customer support?",
    "home.faq.7.text": "You can access our support form",
    "home.faq.7.form-link": "here.",
  })
</script>

<Story name="Primary">
  {#snippet children()}
    <div class="border-c-border-default border-1">
      <HomePage {i18n} />
    </div>
  {/snippet}
</Story>
