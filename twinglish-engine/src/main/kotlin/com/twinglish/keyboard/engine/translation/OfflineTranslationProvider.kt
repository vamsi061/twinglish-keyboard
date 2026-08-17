package com.twinglish.keyboard.engine.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline, rule-based English → Telugu translation provider.
 *
 * The engine is a "sentence grammar": a curated bank of conversational
 * sentence patterns (regular expressions over normalized English) that
 * produce natural Telugu script, followed by a handful of structurally
 * safe verb templates. Translation ALWAYS operates on the complete phrase
 * — there is deliberately NO word-by-word substitution path, because
 * token-by-token output ("which sinima nuvvu want") is unnatural and is
 * treated as worse than no suggestion at all.
 *
 * When no rule matches, the provider returns null and the caller shows the
 * original English (or nothing) instead of a broken partial translation.
 *
 * The keyboard only ever sees [TranslationProvider], so this baseline can
 * be replaced by a remote provider or a learned model later.
 */
class OfflineTranslationProvider : TranslationProvider {

    override val id: String = "offline-rules"
    override val isOnline: Boolean = false

    private data class Rule(val pattern: Regex, val telugu: String, val confidence: Float = 0.92f)

    // A sentence rule: pattern over normalized lowercase input → Telugu
    // template. Templates may reference regex groups ($1, $2 …). Each group
    // is passed through [fillSlot] first, which substitutes known nouns
    // (movie → సినిమా) while leaving proper nouns and code-switched words
    // untouched. Question rules carry their own trailing "?"; the
    // ContextualTranslator dedupes it against user-typed punctuation.
    private val rules: List<Rule> = listOf(
        // ---- specific rules that must beat the generic slot rules below
        //      ("how is (.+)", "i want (.+)", "which (.+) do you want",
        //      "where is (.+)") — order matters: first match wins ----
        Rule(Regex("^how is your day going$"), "నీ రోజు ఎలా ఉంది?"),
        Rule(Regex("^how is your day$"), "నీ రోజు ఎలా ఉంది?"),
        Rule(Regex("^how is your family$"), "నీ కుటుంబం ఎలా ఉంది?"),
        Rule(Regex("^how is everyone$"), "అందరూ ఎలా ఉన్నారు?"),
        Rule(Regex("^how is the weather$"), "weather ఎలా ఉంది?"),
        Rule(Regex("^which one do you want$"), "నీకు ఏది కావాలి?"),
        Rule(Regex("^which one$"), "ఏది?"),
        Rule(Regex("^i want to eat$"), "నాకు తినాలని ఉంది"),
        Rule(Regex("^i want to drink$"), "నాకు తాగాలని ఉంది"),
        Rule(Regex("^i like it$"), "నాకు ఇష్టం"),
        Rule(Regex("^where is the bathroom$"), "bathroom ఎక్కడ?"),
        Rule(Regex("^where is the toilet$"), "toilet ఎక్కడ?"),
        Rule(Regex("^where is the restaurant$"), "restaurant ఎక్కడ?"),
        Rule(Regex("^where is the bus stop$"), "bus stop ఎక్కడ?"),
        Rule(Regex("^where is the station$"), "station ఎక్కడ?"),
        Rule(Regex("^i am in a meeting$"), "నేను meeting లో ఉన్నా"),
        Rule(Regex("^i am in the bus$"), "నేను bus లో ఉన్నా"),

        // ---- human chat: casual everyday sentences a person actually texts ----
        //      (these are exact phrases — placed before the generic slot rules
        //      below so they always win; chat forms drop the formal -ను) ----
        Rule(Regex("^what are you up to$"), "ఏం చేస్తున్నావు?"),
        Rule(Regex("^what are you up to today$"), "ఇవాళ ఏం చేస్తున్నావు?"),
        Rule(Regex("^how is the traffic$"), "traffic ఎలా ఉంది?"),
        Rule(Regex("^traffic is bad$"), "traffic బాగా లేదు"),
        Rule(Regex("^i am stuck in traffic$"), "నేను traffic లో ఇరుక్కున్నా"),
        Rule(Regex("^are you stuck in traffic$"), "నువ్వు traffic లో ఇరుక్కున్నావా?"),
        Rule(Regex("^i am on my way$"), "నేను వస్తున్నా"),
        Rule(Regex("^i am almost there$"), "నేను దాదాపు అక్కడికి చేరుకున్నా"),
        Rule(Regex("^i am leaving now$"), "నేను ఇప్పుడు వెళ్తున్నా"),
        Rule(Regex("^i am leaving tomorrow$"), "నేను రేపు వెళ్తున్నా"),
        Rule(Regex("^i am leaving$"), "నేను వెళ్తున్నా"),
        Rule(Regex("^when are you leaving$"), "ఎప్పుడు వెళ్తున్నావు?"),
        Rule(Regex("^when are you coming$"), "ఎప్పుడు వస్తున్నావు?"),
        Rule(Regex("^i am back$"), "నేను తిరిగి వచ్చా"),
        Rule(Regex("^are you back$"), "నువ్వు తిరిగి వచ్చావా?"),
        Rule(Regex("^i will be back soon$"), "నేను త్వరగా తిరిగి వస్తా"),
        Rule(Regex("^i will be back$"), "నేను తిరిగి వస్తా"),
        Rule(Regex("^i will be there in (.+)$"), "నేను \$1 లో అక్కడ ఉంటా", 0.85f),
        Rule(Regex("^i am waiting for the bus$"), "నేను bus కోసం ఆగుతున్నా"),
        Rule(Regex("^i am on the train$"), "నేను train లో ఉన్నా"),
        Rule(Regex("^i missed the train$"), "నాకు train miss అయింది"),
        Rule(Regex("^i am going to sleep$"), "నేను నిద్రపోతున్నా"),
        Rule(Regex("^i am going to bed$"), "నేను నిద్రపోతున్నా"),
        Rule(Regex("^are you sleeping$"), "నువ్వు నిద్రపోతున్నావా?"),
        Rule(Regex("^did you sleep well$"), "బాగా నిద్రపోయావా?"),
        Rule(Regex("^i slept well$"), "నేను బాగా నిద్రపోయాను"),
        Rule(Regex("^sleep well$"), "బాగా నిద్రపో"),
        Rule(Regex("^sweet dreams$"), "మంచి కలలు"),
        Rule(Regex("^what are you eating$"), "ఏం తింటున్నావు?"),
        Rule(Regex("^i am eating$"), "నేను తింటున్నా"),
        Rule(Regex("^are you eating$"), "నువ్వు తింటున్నావా?"),
        Rule(Regex("^have you eaten$"), "తిన్నావా?"),
        Rule(Regex("^did you have lunch$"), "lunch చేశావా?"),
        Rule(Regex("^did you have breakfast$"), "breakfast చేశావా?"),
        Rule(Regex("^did you have dinner$"), "dinner చేశావా?"),
        Rule(Regex("^i have not had lunch$"), "నేను lunch చేయలేదు"),
        Rule(Regex("^i have not had breakfast$"), "నేను breakfast చేయలేదు"),
        Rule(Regex("^i have not had dinner$"), "నేను dinner చేయలేదు"),
        Rule(Regex("^i am cooking$"), "నేను వంట చేస్తున్నా"),
        Rule(Regex("^are you cooking$"), "నువ్వు వంట చేస్తున్నావా?"),
        Rule(Regex("^food is ready$"), "food ready"),
        Rule(Regex("^dinner is ready$"), "dinner ready"),
        Rule(Regex("^are you hungry$"), "నీకు ఆకలిగా ఉందా?"),
        Rule(Regex("^are you thirsty$"), "నీకు దాహంగా ఉందా?"),
        Rule(Regex("^i am bored$"), "నాకు బోర్ కొడుతోంది"),
        Rule(Regex("^are you bored$"), "నీకు బోర్ కొడుతోందా?"),
        Rule(Regex("^i am excited$"), "నేను excited గా ఉన్నా"),
        Rule(Regex("^i am confused$"), "నాకు confusion గా ఉంది"),
        Rule(Regex("^i am not sure$"), "నాకు ఖచ్చితంగా తెలియదు"),
        Rule(Regex("^i doubt it$"), "నాకు doubt ఉంది"),
        Rule(Regex("^i have a doubt$"), "నాకు ఒక doubt ఉంది"),
        Rule(Regex("^that is funny$"), "అది చాలా funny"),
        Rule(Regex("^you are funny$"), "నువ్వు చాలా funny"),
        Rule(Regex("^i am joking$"), "నేను joke చేస్తున్నా"),
        Rule(Regex("^are you joking$"), "నువ్వు joke చేస్తున్నావా?"),
        Rule(Regex("^just kidding$"), "joke చేస్తున్నా"),
        Rule(Regex("^seriously$"), "నిజంగా"),
        Rule(Regex("^i mean it$"), "నేను నిజంగా చెప్తున్నా"),
        Rule(Regex("^trust me$"), "నన్ను నమ్ము"),
        Rule(Regex("^believe me$"), "నన్ను నమ్ము"),
        Rule(Regex("^i promise$"), "నేను promise చేస్తున్నా"),
        Rule(Regex("^you are right$"), "నువ్వు చెప్పింది నిజం"),
        Rule(Regex("^you are wrong$"), "నువ్వు చెప్పింది తప్పు"),
        Rule(Regex("^what is wrong$"), "ఏమైంది?"),
        Rule(Regex("^what is the problem$"), "problem ఏంటి?"),
        Rule(Regex("^something is wrong$"), "ఏదో జరిగింది"),
        Rule(Regex("^is everything okay$"), "అంతా బాగుందా?"),
        Rule(Regex("^everything is fine$"), "అంతా బాగుంది"),
        Rule(Regex("^is everything ready$"), "అంతా ready?"),
        Rule(Regex("^are you ready$"), "నువ్వు ready?"),
        Rule(Regex("^i am ready$"), "నేను ready"),
        Rule(Regex("^get ready$"), "ready అవు"),
        Rule(Regex("^i am getting ready$"), "నేను ready అవుతున్నా"),
        Rule(Regex("^wait a minute$"), "ఒక నిమిషం ఆగు"),
        Rule(Regex("^one minute$"), "ఒక నిమిషం"),
        Rule(Regex("^give me a minute$"), "నాకు ఒక నిమిషం ఇవ్వు"),
        Rule(Regex("^hold on$"), "కాసేపు ఆగు"),
        Rule(Regex("^take your time$"), "నెమ్మదిగా తీసుకో"),
        Rule(Regex("^come with me$"), "నాతో రా"),
        Rule(Regex("^come with us$"), "మాతో రా"),
        Rule(Regex("^i will come with you$"), "నేను నీతో వస్తా"),
        Rule(Regex("^are you coming with me$"), "నువ్వు నాతో వస్తున్నావా?"),
        Rule(Regex("^i am with my family$"), "నేను family తో ఉన్నా"),
        Rule(Regex("^i am with my friends$"), "నేను friends తో ఉన్నా"),
        Rule(Regex("^who is with you$"), "నీ దగ్గర ఎవరు ఉన్నారు?"),
        Rule(Regex("^i am alone$"), "నేను ఒంటరిగా ఉన్నా"),
        Rule(Regex("^are you alone$"), "నువ్వు ఒంటరిగా ఉన్నావా?"),
        Rule(Regex("^i am at the office$"), "నేను office లో ఉన్నా"),
        Rule(Regex("^are you at the office$"), "నువ్వు office లో ఉన్నావా?"),
        Rule(Regex("^i am at the market$"), "నేను market లో ఉన్నా"),
        Rule(Regex("^are you at the market$"), "నువ్వు market లో ఉన్నావా?"),
        Rule(Regex("^i am going to the (gym|market|office|school|college|temple|hospital|mall|park|beach|shop|bank|station|airport)$"), "నేను \$1 కి వెళ్తున్నా"),
        Rule(Regex("^i am going to work$"), "నేను పనికి వెళ్తున్నా"),
        Rule(Regex("^i am going to school$"), "నేను school కి వెళ్తున్నా"),
        Rule(Regex("^i am going to college$"), "నేను college కి వెళ్తున్నా"),
        Rule(Regex("^i am done with work$"), "నా పని అయిపోయింది"),
        Rule(Regex("^are you done$"), "నీ పని అయిపోయిందా?"),
        Rule(Regex("^i have to go to work$"), "నేను పనికి వెళ్లాలి"),
        Rule(Regex("^i have class$"), "నాకు class ఉంది"),
        Rule(Regex("^when is your exam$"), "నీ exam ఎప్పుడు?"),
        Rule(Regex("^i passed the exam$"), "నేను exam లో pass అయ్యాను"),
        Rule(Regex("^i failed the exam$"), "నేను exam లో fail అయ్యాను"),
        Rule(Regex("^i will call you back$"), "నేను నీకు తర్వాత call చేస్తా"),
        Rule(Regex("^call me back$"), "నాకు తర్వాత call చెయ్యి"),
        Rule(Regex("^call me when you are free$"), "నీకు time ఉన్నప్పుడు నాకు call చెయ్యి"),
        Rule(Regex("^i will text you$"), "నేను నీకు text చేస్తా"),
        Rule(Regex("^i am on a call$"), "నేను call లో ఉన్నా"),
        Rule(Regex("^are you on a call$"), "నువ్వు call లో ఉన్నావా?"),
        Rule(Regex("^i am online$"), "నేను online లో ఉన్నా"),
        Rule(Regex("^are you online$"), "నువ్వు online లో ఉన్నావా?"),
        Rule(Regex("^my phone is dead$"), "నా phone battery అయిపోయింది"),
        Rule(Regex("^my battery is low$"), "నా battery చాలా తక్కువ"),
        Rule(Regex("^i am charging my phone$"), "నేను phone charge చేస్తున్నా"),
        Rule(Regex("^i missed you$"), "నీ కోసం miss అయ్యాను"),
        Rule(Regex("^i miss you too$"), "నేను కూడా నిన్ను miss అవుతున్నా"),
        Rule(Regex("^i want to see you$"), "నిన్ను చూడాలని ఉంది"),
        Rule(Regex("^i want to meet you$"), "నిన్ను కలవాలని ఉంది"),
        Rule(Regex("^i will meet you$"), "నేను నిన్ను కలుస్తా"),
        Rule(Regex("^i will meet you tomorrow$"), "నేను నిన్ను రేపు కలుస్తా"),
        Rule(Regex("^let us meet$"), "కలుద్దాం"),
        Rule(Regex("^let us meet tomorrow$"), "రేపు కలుద్దాం"),
        Rule(Regex("^let us meet today$"), "ఇవాళ కలుద్దాం"),
        Rule(Regex("^when can we meet$"), "ఎప్పుడు కలుద్దాం?"),
        Rule(Regex("^where should we meet$"), "ఎక్కడ కలుద్దాం?"),
        Rule(Regex("^shall we meet$"), "కలుద్దాం?"),
        Rule(Regex("^are you free tomorrow$"), "రేపు నువ్వు ఖాళీగా ఉన్నావా?"),
        Rule(Regex("^are you free today$"), "ఇవాళ నువ్వు ఖాళీగా ఉన్నావా?"),
        Rule(Regex("^are you free this weekend$"), "ఈ weekend నువ్వు ఖాళీగా ఉన్నావా?"),
        Rule(Regex("^i am free tomorrow$"), "నేను రేపు ఖాళీగా ఉన్నా"),
        Rule(Regex("^i am free today$"), "నేను ఇవాళ ఖాళీగా ఉన్నా"),
        Rule(Regex("^i am free this weekend$"), "ఈ weekend నేను ఖాళీగా ఉన్నా"),
        Rule(Regex("^what are your plans$"), "నీ plans ఏంటి?"),
        Rule(Regex("^what is your plan$"), "నీ plan ఏంటి?"),
        Rule(Regex("^any plans$"), "ఏమైనా plans ఉన్నాయా?"),
        Rule(Regex("^i have no plans$"), "నాకు plans లేవు"),
        Rule(Regex("^how is your health$"), "నీ ఆరోగ్యం ఎలా ఉంది?"),
        Rule(Regex("^how are your parents$"), "నీ parents ఎలా ఉన్నారు?"),
        Rule(Regex("^are you feeling better$"), "నీకు ఇప్పుడు బాగా అనిపిస్తోందా?"),
        Rule(Regex("^i am feeling better$"), "నాకు ఇప్పుడు బాగా అనిపిస్తోంది"),
        Rule(Regex("^i am getting better$"), "నేను బాగవుతున్నా"),
        Rule(Regex("^how was the movie$"), "movie ఎలా ఉంది?"),
        Rule(Regex("^the movie was good$"), "movie బాగుంది"),
        Rule(Regex("^i liked the movie$"), "నాకు movie నచ్చింది"),
        Rule(Regex("^did you like the movie$"), "నీకు movie నచ్చిందా?"),
        Rule(Regex("^how was the party$"), "party ఎలా ఉంది?"),
        Rule(Regex("^how was your trip$"), "నీ trip ఎలా ఉంది?"),
        Rule(Regex("^i am glad$"), "నాకు సంతోషంగా ఉంది"),
        Rule(Regex("^i am glad to hear that$"), "అది విన్నందుకు సంతోషం"),
        Rule(Regex("^that is great$"), "చాలా బాగుంది"),
        Rule(Regex("^that is awesome$"), "చాలా బాగుంది"),
        Rule(Regex("^well done$"), "బాగా చేశావు"),
        Rule(Regex("^good job$"), "బాగా చేశావు"),
        Rule(Regex("^keep it up$"), "బాగా చేస్తున్నావు"),
        Rule(Regex("^good luck for your exam$"), "నీ exam కి all the best"),
        Rule(Regex("^see you tomorrow$"), "రేపు కలుద్దాం"),
        Rule(Regex("^see you soon$"), "త్వరలో కలుద్దాం"),
        Rule(Regex("^talk to you soon$"), "త్వరలో మాట్లాడుదాం"),
        Rule(Regex("^how much did it cost$"), "దాని ధర ఎంత?"),
        Rule(Regex("^it is too expensive$"), "చాలా ఖరీదు"),

        // ---- what … ----
        Rule(Regex("^what are you doing today$"), "ఏం చేస్తున్నావు ఇవాళ?"),
        Rule(Regex("^what are you doing now$"), "ఏం చేస్తున్నావు ఇప్పుడు?"),
        Rule(Regex("^what are you doing tomorrow$"), "రేపు ఏం చేస్తున్నావు?"),
        Rule(Regex("^what are you doing$"), "ఏం చేస్తున్నావు?"),
        Rule(Regex("^what are you doing (tonight|this weekend|this evening)$"), "\$1 ఏం చేస్తున్నావు?", 0.8f),
        Rule(Regex("^what are you watching$"), "ఏం చూస్తున్నావు?"),
        Rule(Regex("^what are you reading$"), "ఏం చదువుతున్నావు?"),
        Rule(Regex("^what happened$"), "ఏమైంది?"),
        Rule(Regex("^what is your name$"), "నీ పేరు ఏంటి?"),
        Rule(Regex("^what is this$"), "ఇది ఏంటి?"),
        Rule(Regex("^what do you want$"), "నీకు ఏం కావాలి?"),
        Rule(Regex("^what about you$"), "నువ్వు ఎలా ఉన్నావు?"),
        Rule(Regex("^what did you eat$"), "ఏం తిన్నావు?"),

        // ---- how … ----
        Rule(Regex("^how are you$"), "ఎలా ఉన్నావు?"),
        Rule(Regex("^how are you doing$"), "ఎలా ఉన్నావు?"),
        Rule(Regex("^how is it going$"), "ఎలా ఉంది?"),
        Rule(Regex("^how much is this$"), "ఇది ఎంత?"),
        Rule(Regex("^how much$"), "ఎంత?"),
        Rule(Regex("^how is (.+)$"), "\$1 ఎలా ఉంది?", 0.8f),

        // ---- where … ----
        Rule(Regex("^where are you going tomorrow$"), "రేపు ఎక్కడికి వెళ్తున్నావు?"),
        Rule(Regex("^where are you going today$"), "ఇవాళ ఎక్కడికి వెళ్తున్నావు?"),
        Rule(Regex("^where are you going$"), "ఎక్కడికి వెళ్తున్నావు?"),
        Rule(Regex("^where are you$"), "ఎక్కడ ఉన్నావు?"),
        Rule(Regex("^where do you live$"), "నువ్వు ఎక్కడ ఉంటున్నావు?"),
        Rule(Regex("^where did you go$"), "ఎక్కడికి వెళ్ళావు?"),
        Rule(Regex("^where is (.+)$"), "\$1 ఎక్కడ ఉంది?", 0.8f),

        // ---- which … ----
        Rule(Regex("^which (.+) you want$"), "ఏ \$1 కావాలి?", 0.8f),
        Rule(Regex("^which (.+) do you want$"), "ఏ \$1 కావాలి?", 0.8f),
        Rule(Regex("^which (.+) are you watching$"), "ఏ \$1 చూస్తున్నావు?", 0.8f),

        // ---- why / when / who ----
        Rule(Regex("^why are you late$"), "ఎందుకు late అయ్యావు?"),
        Rule(Regex("^why did you come$"), "ఎందుకు వచ్చావు?"),
        Rule(Regex("^why$"), "ఎందుకు?"),
        Rule(Regex("^when will you come$"), "ఎప్పుడు వస్తున్నావు?"),
        Rule(Regex("^when did you come$"), "ఎప్పుడు వచ్చావు?"),
        Rule(Regex("^when$"), "ఎప్పుడు?"),
        Rule(Regex("^who are you$"), "నువ్వు ఎవరు?"),

        // ---- yes / no questions ----
        Rule(Regex("^did you eat$"), "తిన్నావా?"),
        Rule(Regex("^did you eat (.+)$"), "\$1 తిన్నావా?", 0.8f),
        Rule(Regex("^are you coming$"), "నువ్వు వస్తున్నావా?"),
        Rule(Regex("^are you coming today$"), "ఇవాళ వస్తున్నావా?"),
        Rule(Regex("^are you coming tomorrow$"), "రేపు వస్తున్నావా?"),
        Rule(Regex("^are you okay$"), "నువ్వు బాగున్నావా?"),
        Rule(Regex("^are you free$"), "నువ్వు ఖాళీగా ఉన్నావా?"),
        Rule(Regex("^are you sure$"), "నీకు ఖచ్చితంగా తెలుసా?"),
        Rule(Regex("^are you at home$"), "నువ్వు ఇంట్లో ఉన్నావా?"),
        Rule(Regex("^are you watching (.+)$"), "\$1 చూస్తున్నావా?", 0.8f),
        Rule(Regex("^do you want (.+)$"), "నీకు \$1 కావాలా?", 0.8f),
        Rule(Regex("^do you like (.+)$"), "నీకు \$1 ఇష్టమా?", 0.8f),
        Rule(Regex("^do you understand$"), "అర్థమైందా?"),
        Rule(Regex("^do you know$"), "నీకు తెలుసా?"),
        Rule(Regex("^will you come tomorrow$"), "రేపు వస్తావా?"),
        Rule(Regex("^can you help me$"), "నాకు సహాయం చేస్తావా?"),

        // ---- i … statements ----
        Rule(Regex("^i am coming tomorrow$"), "నేను రేపు వస్తాను"),
        Rule(Regex("^i will come tomorrow$"), "నేను రేపు వస్తాను"),
        Rule(Regex("^i am going home$"), "నేను ఇంటికి వెళ్తున్నాను"),
        Rule(Regex("^i am going to (.+) tomorrow$"), "నేను రేపు \$1 కి వెళ్తున్నాను", 0.8f),
        Rule(Regex("^i am going to (.+) today$"), "నేను ఇవాళ \$1 కి వెళ్తున్నాను", 0.8f),
        Rule(Regex("^i am going to (.+) tonight$"), "నేను ఈ రాత్రి \$1 కి వెళ్తున్నాను", 0.8f),
        Rule(Regex("^i am going to (.+)$"), "నేను \$1 కి వెళ్తున్నాను", 0.8f),
        Rule(Regex("^i am going$"), "నేను వెళ్తున్నా"),
        Rule(Regex("^i am coming$"), "నేను వస్తున్నా"),
        Rule(Regex("^i am watching (.+)$"), "నేను \$1 చూస్తున్నాను", 0.8f),
        Rule(Regex("^i am at home$"), "నేను ఇంట్లో ఉన్నాను"),
        Rule(Regex("^i am in (.+)$"), "నేను \$1 లో ఉన్నాను", 0.8f),
        Rule(Regex("^i am fine$"), "నేను బాగున్నాను"),
        Rule(Regex("^i am busy$"), "నేను బిజీగా ఉన్నాను"),
        Rule(Regex("^i am tired$"), "నేను అలసిపోయాను"),
        Rule(Regex("^i am hungry$"), "నాకు ఆకలిగా ఉంది"),
        Rule(Regex("^i am thirsty$"), "నాకు దాహంగా ఉంది"),
        Rule(Regex("^i am waiting$"), "నేను ఆగుతున్నాను"),
        Rule(Regex("^i am sorry$"), "నన్ను క్షమించు"),
        Rule(Regex("^i am sure$"), "నాకు ఖచ్చితంగా తెలుసు"),
        Rule(Regex("^i will call you later$"), "తర్వాత నీకు call చేస్తా"),
        Rule(Regex("^i will call you$"), "నేను నీకు call చేస్తాను"),
        Rule(Regex("^i love you$"), "నేను నిన్ను ప్రేమిస్తున్నాను"),
        Rule(Regex("^i miss you$"), "నీ కోసం miss అవుతున్నాను"),
        Rule(Regex("^i know$"), "నాకు తెలుసు"),
        Rule(Regex("^i do not know$"), "నాకు తెలియదు"),
        Rule(Regex("^i do not understand$"), "నాకు అర్థం కాలేదు"),
        Rule(Regex("^i do not want$"), "నాకు వద్దు"),
        Rule(Regex("^i want water$"), "నాకు నీళ్ళు కావాలి"),
        Rule(Regex("^i want (a )?coffee$"), "నాకు coffee కావాలి"),
        Rule(Regex("^i want (a )?tea$"), "నాకు tea కావాలి"),
        Rule(Regex("^i want (.+)$"), "నాకు \$1 కావాలి", 0.7f),
        Rule(Regex("^i like (.+)$"), "నాకు \$1 ఇష్టం", 0.7f),
        Rule(Regex("^my name is (.+)$"), "నా పేరు \$1", 0.85f),

        // ---- requests / small talk ----
        Rule(Regex("^call me later$"), "తర్వాత నాకు call చెయ్యి"),
        Rule(Regex("^call me$"), "నాకు call చెయ్యి"),
        Rule(Regex("^please help me$"), "దయచేసి నాకు సహాయం చెయ్యి"),
        Rule(Regex("^talk to you later$"), "తర్వాత మాట్లాడుదాం"),
        Rule(Regex("^see you later$"), "తర్వాత కలుద్దాం"),
        Rule(Regex("^come here$"), "ఇక్కడికి రా"),
        Rule(Regex("^go away$"), "వెళ్ళిపో"),
        Rule(Regex("^wait$"), "ఆగు"),
        Rule(Regex("^wait (a moment|for me)$"), "కాసేపు ఆగు"),
        Rule(Regex("^let us go$"), "వెళ్దాం"),
        Rule(Regex("^no problem$"), "పర్వాలేదు"),
        Rule(Regex("^it is okay$"), "పర్వాలేదు"),
        Rule(Regex("^never mind$"), "పర్వాలేదు"),
        Rule(Regex("^take care$"), "జాగ్రత్త"),
        Rule(Regex("^good luck$"), "శుభాకాంక్షలు"),
        Rule(Regex("^congratulations$"), "అభినందనలు"),
        Rule(Regex("^happy birthday$"), "పుట్టినరోజు శుభాకాంక్షలు"),
        Rule(Regex("^happy new year$"), "నూతన సంవత్సర శుభాకాంక్షలు"),
        Rule(Regex("^good morning$"), "శుభోదయం"),
        Rule(Regex("^good night$"), "శుభరాత్రి"),
        Rule(Regex("^thank you$"), "ధన్యవాదాలు"),
        Rule(Regex("^really$"), "నిజంగా?"),
        Rule(Regex("^sorry$"), "సారీ"),
        Rule(Regex("^yes$"), "ఔను"),
        Rule(Regex("^no$"), "కాదు"),

        // ---- greetings / small talk ----
        Rule(Regex("^good afternoon$"), "శుభ మధ్యాహ్నం"),
        Rule(Regex("^good evening$"), "శుభ సాయంత్రం"),
        Rule(Regex("^good day$"), "శుభ దినం"),
        Rule(Regex("^bye$"), "bye"),
        Rule(Regex("^see you$"), "కలుద్దాం"),
        Rule(Regex("^welcome$"), "స్వాగతం"),
        Rule(Regex("^how have you been$"), "ఇన్నాళ్ళు ఎలా ఉన్నావు?"),
        Rule(Regex("^how was your day$"), "నీ రోజు ఎలా ఉంది?"),
        Rule(Regex("^how do you do$"), "ఎలా ఉన్నావు?"),
        Rule(Regex("^whats up$"), "ఏముంది?"),
        Rule(Regex("^what is up$"), "ఏముంది?"),
        Rule(Regex("^what is going on$"), "ఏం జరుగుతోంది?"),
        Rule(Regex("^nice to meet you$"), "నిన్ను కలవడం సంతోషం"),
        Rule(Regex("^long time no see$"), "చాలా రోజులైంది కదా"),
        Rule(Regex("^ok bye$"), "సరే bye"),

        // ---- introductions / personal ----
        Rule(Regex("^i am from (.+)$"), "నేను \$1 నుంచి వచ్చాను", 0.8f),
        Rule(Regex("^where are you from$"), "నువ్వు ఎక్కడ నుంచి వచ్చావు?"),
        Rule(Regex("^i am studying$"), "నేను చదువుతున్నాను"),
        Rule(Regex("^i am working$"), "నేను పని చేస్తున్నాను"),
        Rule(Regex("^i am a student$"), "నేను student ని"),
        Rule(Regex("^i am a teacher$"), "నేను teacher ని"),
        Rule(Regex("^i am a doctor$"), "నేను doctor ని"),
        Rule(Regex("^i am married$"), "నాకు పెళ్లయింది"),
        Rule(Regex("^i am single$"), "నేను single ని"),
        Rule(Regex("^i have a brother$"), "నాకు ఒక brother ఉన్నాడు"),
        Rule(Regex("^i have a sister$"), "నాకు ఒక sister ఉంది"),
        Rule(Regex("^do you have a brother$"), "నీకు brother ఉన్నాడా?"),
        Rule(Regex("^do you have a sister$"), "నీకు sister ఉందా?"),
        Rule(Regex("^how old are you$"), "నీ వయసు ఎంత?"),

        // ---- food / eating ----
        Rule(Regex("^i ate$"), "నేను తిన్నాను"),
        Rule(Regex("^i have eaten$"), "నేను తిన్నాను"),
        Rule(Regex("^i have not eaten$"), "నేను తినలేదు"),
        Rule(Regex("^i had breakfast$"), "నేను breakfast చేశాను"),
        Rule(Regex("^i had lunch$"), "నేను lunch చేశాను"),
        Rule(Regex("^i had dinner$"), "నేను dinner చేశాను"),
        Rule(Regex("^let us eat$"), "తినుదాం"),
        Rule(Regex("^let us have lunch$"), "lunch చేద్దాం"),
        Rule(Regex("^let us have dinner$"), "dinner చేద్దాం"),
        Rule(Regex("^what is for lunch$"), "lunch ఏంటి?"),
        Rule(Regex("^what is for dinner$"), "dinner ఏంటి?"),
        Rule(Regex("^the food is tasty$"), "food బాగుంది"),
        Rule(Regex("^this food is tasty$"), "ఈ food చాలా బాగుంది"),
        Rule(Regex("^i am full$"), "నాకు కడుపు నిండింది"),

        // ---- time / plans ----
        Rule(Regex("^what time is it$"), "ఇప్పుడు ఎంత సమయం?"),
        Rule(Regex("^what time$"), "ఎంత సమయం?"),
        Rule(Regex("^are you free now$"), "ఇప్పుడు నువ్వు ఖాళీగా ఉన్నావా?"),
        Rule(Regex("^are you busy$"), "నువ్వు busy గా ఉన్నావా?"),
        Rule(Regex("^i am free$"), "నేను ఖాళీగా ఉన్నా"),
        Rule(Regex("^i am free now$"), "నేను ఇప్పుడు ఖాళీగా ఉన్నా"),
        Rule(Regex("^i have time$"), "నాకు సమయం ఉంది"),
        Rule(Regex("^i do not have time$"), "నాకు సమయం లేదు"),
        Rule(Regex("^i am late$"), "నేను late అయ్యాను"),
        Rule(Regex("^i will be late$"), "నేను late అవుతాను"),
        Rule(Regex("^i will reach by (.+)$"), "నేను \$1 లోపల చేరుకుంటాను", 0.8f),
        Rule(Regex("^when will you be free$"), "నీకు ఎప్పుడు time ఉంటుంది?"),
        Rule(Regex("^i am going to office$"), "నేను office కి వెళ్తున్నాను"),
        Rule(Regex("^are you going to office$"), "నువ్వు office కి వెళ్తున్నావా?"),

        // ---- travel / location ----
        Rule(Regex("^are you going to (.+)$"), "నువ్వు \$1 కి వెళ్తున్నావా?", 0.8f),
        Rule(Regex("^i am coming to (.+)$"), "నేను \$1 కి వస్తున్నాను", 0.8f),
        Rule(Regex("^where are you now$"), "నువ్వు ఇప్పుడు ఎక్కడ ఉన్నావు?"),
        Rule(Regex("^i am on the bus$"), "నేను bus లో ఉన్నా"),
        Rule(Regex("^i missed the bus$"), "నాకు bus miss అయింది"),
        Rule(Regex("^how do i go to (.+)$"), "నేను \$1 కి ఎలా వెళ్లాలి?", 0.8f),
        Rule(Regex("^let us go to (.+)$"), "\$1 కి వెళ్దాం", 0.8f),
        Rule(Regex("^shall we go$"), "వెళ్దాం?"),
        Rule(Regex("^shall we go to (.+)$"), "\$1 కి వెళ్దాం?", 0.8f),
        Rule(Regex("^i reached home$"), "నేను ఇంటికి చేరుకున్నాను"),
        Rule(Regex("^i reached$"), "నేను చేరుకున్నాను"),
        Rule(Regex("^i am home$"), "నేను ఇంట్లో ఉన్నా"),
        Rule(Regex("^are you home$"), "నువ్వు ఇంట్లో ఉన్నావా?"),
        Rule(Regex("^i am outside$"), "నేను బయట ఉన్నా"),
        Rule(Regex("^i am inside$"), "నేను లోపల ఉన్నా"),
        Rule(Regex("^come inside$"), "లోపలికి రా"),
        Rule(Regex("^come fast$"), "త్వరగా రా"),
        Rule(Regex("^hurry up$"), "త్వరగా రా"),
        Rule(Regex("^slow down$"), "నెమ్మదిగా వెళ్ళు"),
        Rule(Regex("^turn right$"), "కుడి వైపు వెళ్ళు"),
        Rule(Regex("^turn left$"), "ఎడమ వైపు వెళ్ళు"),
        Rule(Regex("^go straight$"), "నేరుగా వెళ్ళు"),
        Rule(Regex("^i am lost$"), "నేను దారి తప్పాను"),

        // ---- shopping / money ----
        Rule(Regex("^how much does it cost$"), "దీని ధర ఎంత?"),
        Rule(Regex("^it is expensive$"), "దీని ధర చాలా ఎక్కువ"),
        Rule(Regex("^it is cheap$"), "దీని ధర తక్కువ"),
        Rule(Regex("^i will buy this$"), "నేను దీన్ని కొంటాను"),
        Rule(Regex("^do you have money$"), "నీ దగ్గర డబ్బు ఉందా?"),
        Rule(Regex("^i have money$"), "నా దగ్గర డబ్బు ఉంది"),
        Rule(Regex("^i do not have money$"), "నా దగ్గర డబ్బు లేదు"),

        // ---- work / study ----
        Rule(Regex("^i am at work$"), "నేను పని మీద ఉన్నా"),
        Rule(Regex("^i have a meeting$"), "నాకు meeting ఉంది"),
        Rule(Regex("^i am busy with work$"), "నేను పనిలో busy గా ఉన్నా"),
        Rule(Regex("^when is the meeting$"), "meeting ఎప్పుడు?"),
        Rule(Regex("^how was work$"), "పని ఎలా ఉంది?"),
        Rule(Regex("^how is work$"), "పని ఎలా ఉంది?"),
        Rule(Regex("^i have homework$"), "నాకు homework ఉంది"),
        Rule(Regex("^i have an exam$"), "నాకు exam ఉంది"),
        Rule(Regex("^how was the exam$"), "exam ఎలా ఉంది?"),
        Rule(Regex("^did you finish your work$"), "నీ పని అయిపోయిందా?"),
        Rule(Regex("^i finished my work$"), "నా పని అయిపోయింది"),

        // ---- feelings / health ----
        Rule(Regex("^how are you feeling$"), "నీకు ఎలా అనిపిస్తోంది?"),
        Rule(Regex("^i am happy$"), "నేను సంతోషంగా ఉన్నా"),
        Rule(Regex("^i am sad$"), "నేను బాధగా ఉన్నా"),
        Rule(Regex("^i am not fine$"), "నేను బాగా లేను"),
        Rule(Regex("^i am not feeling well$"), "నాకు బాగా లేదు"),
        Rule(Regex("^i am sick$"), "నాకు జబ్బు చేసింది"),
        Rule(Regex("^i have a cold$"), "నాకు జలుబు చేసింది"),
        Rule(Regex("^i have a fever$"), "నాకు జ్వరం వచ్చింది"),
        Rule(Regex("^i have a headache$"), "నాకు తల నొప్పి వస్తోంది"),
        Rule(Regex("^i am sleepy$"), "నాకు నిద్ర వస్తోంది"),
        Rule(Regex("^get well soon$"), "త్వరగా బాగుపడు"),

        // ---- phone / communication ----
        Rule(Regex("^i called you$"), "నేను నీకు call చేశాను"),
        Rule(Regex("^did you call me$"), "నాకు call చేశావా?"),
        Rule(Regex("^i missed your call$"), "నీ call miss అయింది"),
        Rule(Regex("^i am calling you$"), "నేను నీకు call చేస్తున్నా"),
        Rule(Regex("^message me$"), "నాకు message పంపు"),
        Rule(Regex("^text me$"), "నాకు text చెయ్యి"),
        Rule(Regex("^i sent you a message$"), "నీకు message పంపాను"),
        Rule(Regex("^did you get my message$"), "నా message వచ్చిందా?"),
        Rule(Regex("^are you on whatsapp$"), "నువ్వు whatsapp లో ఉన్నావా?"),
        Rule(Regex("^i am on whatsapp$"), "నేను whatsapp లో ఉన్నా"),
        Rule(Regex("^send me the photo$"), "నాకు photo పంపు"),
        Rule(Regex("^send me the details$"), "నాకు details పంపు"),

        // ---- opinions / agreement ----
        Rule(Regex("^i think so$"), "నాకు అలా అనిపిస్తుంది"),
        Rule(Regex("^i agree$"), "నేను agree అవుతున్నా"),
        Rule(Regex("^i love it$"), "నాకు చాలా ఇష్టం"),
        Rule(Regex("^i do not like it$"), "నాకు ఇష్టం లేదు"),
        Rule(Regex("^that is good$"), "అది బాగుంది"),
        Rule(Regex("^that is nice$"), "అది బాగుంది"),
        Rule(Regex("^that is bad$"), "అది బాగా లేదు"),
        Rule(Regex("^it is good$"), "బాగుంది"),
        Rule(Regex("^it is fine$"), "పర్వాలేదు"),
        Rule(Regex("^sounds good$"), "బాగుంది"),
        Rule(Regex("^sounds great$"), "చాలా బాగుంది"),
        Rule(Regex("^i understand$"), "నాకు అర్థమైంది"),
        Rule(Regex("^i understood$"), "నాకు అర్థమైంది"),
        Rule(Regex("^got it$"), "అర్థమైంది"),
        Rule(Regex("^right$"), "నిజమే"),
        Rule(Regex("^exactly$"), "నిజంగా"),
        Rule(Regex("^maybe$"), "బహుశా"),
        Rule(Regex("^of course$"), "తప్పకుండా"),
        Rule(Regex("^sure$"), "తప్పకుండా"),
        Rule(Regex("^i will try$"), "నేను try చేస్తా"),

        // ---- requests / help ----
        Rule(Regex("^help me$"), "నాకు సహాయం చెయ్యి"),
        Rule(Regex("^i need help$"), "నాకు సహాయం కావాలి"),
        Rule(Regex("^i need (.+)$"), "నాకు \$1 కావాలి", 0.8f),
        Rule(Regex("^do you need (.+)$"), "నీకు \$1 కావాలా?", 0.8f),
        Rule(Regex("^please call me$"), "దయచేసి నాకు call చెయ్యి"),
        Rule(Regex("^please wait$"), "కాసేపు ఆగు"),

        // ---- common questions ----
        Rule(Regex("^what do you mean$"), "ఏం అంటున్నావు?"),
        Rule(Regex("^what do you think$"), "నీకు ఏం అనిపిస్తుంది?"),
        Rule(Regex("^who is this$"), "ఇది ఎవరు?"),
        Rule(Regex("^who is that$"), "అది ఎవరు?"),
        Rule(Regex("^whose is this$"), "ఇది ఎవరిది?"),
        Rule(Regex("^how do you know$"), "నీకు ఎలా తెలుసు?"),
        Rule(Regex("^how do you say (.+) in telugu$"), "\$1 ని తెలుగులో ఎలా అంటారు?", 0.8f),
        Rule(Regex("^how long$"), "ఎంత సేపు?"),
        Rule(Regex("^how far$"), "ఎంత దూరం?"),
        Rule(Regex("^how many$"), "ఎన్ని?"),
        Rule(Regex("^how many people$"), "ఎంత మంది?"),
        Rule(Regex("^how many days$"), "ఎన్ని రోజులు?"),
        Rule(Regex("^which is better$"), "ఏది బాగుంది?"),
        Rule(Regex("^is it far$"), "దూరమా?"),
        Rule(Regex("^is it near$"), "దగ్గరా?"),
        Rule(Regex("^is it okay$"), "సరేనా?"),
        Rule(Regex("^is that true$"), "అది నిజమా?"),
        Rule(Regex("^what did you say$"), "ఏమన్నావు?"),
        Rule(Regex("^what do you do$"), "నువ్వు ఏం చేస్తావు?"),

        // ---- possession ----
        Rule(Regex("^i have a car$"), "నాకు car ఉంది"),
        Rule(Regex("^i have a bike$"), "నాకు bike ఉంది"),
        Rule(Regex("^i have a phone$"), "నాకు phone ఉంది"),
        Rule(Regex("^do you have a car$"), "నీకు car ఉందా?"),
        Rule(Regex("^i do not have a car$"), "నాకు car లేదు"),

        // ---- weather ----
        Rule(Regex("^it is hot$"), "చాలా వేడిగా ఉంది"),
        Rule(Regex("^it is cold$"), "చాలా చలిగా ఉంది"),
        Rule(Regex("^it is raining$"), "వర్షం పడుతోంది"),
        Rule(Regex("^it is sunny$"), "ఎండగా ఉంది"),
        Rule(Regex("^is it raining$"), "వర్షం పడుతోందా?"),
        Rule(Regex("^the weather is nice today$"), "ఈ రోజు weather చాలా బాగుంది"),

        // ---- waiting / listening ----
        Rule(Regex("^i am waiting for you$"), "నేను నీ కోసం ఆగుతున్నా"),
        Rule(Regex("^are you waiting$"), "నువ్వు ఆగుతున్నావా?"),
        Rule(Regex("^where are you waiting$"), "నువ్వు ఎక్కడ ఆగుతున్నావు?"),
        Rule(Regex("^i am coming now$"), "నేను ఇప్పుడు వస్తున్నా"),
        Rule(Regex("^wait for me$"), "నా కోసం ఆగు"),
        Rule(Regex("^i will wait$"), "నేను ఆగుతాను"),
        Rule(Regex("^are you there$"), "నువ్వు ఉన్నావా?"),
        Rule(Regex("^are you listening$"), "నువ్వు వింటున్నావా?"),
        Rule(Regex("^i am listening$"), "నేను వింటున్నా"),
        Rule(Regex("^can you hear me$"), "నువ్వు నన్ను వింటున్నావా?"),
        Rule(Regex("^speak slowly$"), "నెమ్మదిగా మాట్లాడు"),
        Rule(Regex("^please speak slowly$"), "దయచేసి నెమ్మదిగా మాట్లాడండి"),
        Rule(Regex("^say it again$"), "మళ్ళీ చెప్పు"),
        Rule(Regex("^one more time$"), "మరోసారి"),
        Rule(Regex("^can you repeat$"), "మళ్ళీ చెప్పండి"),

        // ---- memory / reassurance ----
        Rule(Regex("^it is my fault$"), "నా తప్పు"),
        Rule(Regex("^i forgot$"), "నేను మర్చిపోయాను"),
        Rule(Regex("^i remember$"), "నాకు గుర్తుంది"),
        Rule(Regex("^i do not remember$"), "నాకు గుర్తులేదు"),
        Rule(Regex("^do not worry$"), "చింతించకు"),
        Rule(Regex("^do not worry about it$"), "దాని గురించి చింతించకు"),
        Rule(Regex("^it does not matter$"), "పర్వాలేదు"),
        Rule(Regex("^have fun$"), "enjoy చెయ్యి"),
        Rule(Regex("^enjoy$"), "enjoy చెయ్యి"),
        Rule(Regex("^be careful$"), "జాగ్రత్తగా ఉండు"),
        Rule(Regex("^take rest$"), "rest తీసుకో"),
        Rule(Regex("^drive safe$"), "జాగ్రత్తగా drive చెయ్యి"),
    )

    // Known conversational nouns/words substituted inside rule slots so
    // "which movie you want" becomes "ఏ సినిమా కావాలి?" instead of a raw
    // "movie" — while proper nouns (Hyderabad) pass through untouched.
    private val slotWords = mapOf(
        "movie" to "సినిమా", "film" to "సినిమా", "song" to "పాట",
        "money" to "డబ్బు", "time" to "సమయం",
        "place" to "చోటు", "work" to "పని", "home" to "ఇల్లు", "house" to "ఇల్లు",
        "water" to "నీళ్ళు", "food" to "అన్నం",
        "milk" to "పాలు", "book" to "పుస్తకం",
        "city" to "నగరం",
        "family" to "కుటుంబం", "car" to "కారు", "bus" to "బస్సు",
        "train" to "రైలు", "name" to "పేరు",
        "love" to "ప్రేమ", "life" to "జీవితం", "day" to "రోజు",
        "week" to "వారం", "month" to "నెల", "year" to "సంవత్సరం",
        "today" to "ఇవాళ", "tomorrow" to "రేపు", "yesterday" to "నిన్న",
        "now" to "ఇప్పుడు", "later" to "తర్వాత", "tonight" to "ఈ రాత్రి",
        "morning" to "ఉదయం", "evening" to "సాయంత్రం", "night" to "రాత్రి",
        "my" to "నా", "your" to "నీ", "his" to "అతని", "her" to "ఆమె",
        "our" to "మన", "this" to "ఇది", "that" to "అది",
        // Common places / objects — kept English where chat prefers it.
        "office" to "office", "market" to "మార్కెట్", "shop" to "షాప్",
        "store" to "షాప్", "bank" to "బ్యాంక్", "hospital" to "hospital",
        "school" to "school", "college" to "college", "temple" to "గుడి",
        "church" to "church", "park" to "park", "beach" to "beach",
        "airport" to "airport", "station" to "station", "mall" to "mall",
        "hotel" to "hotel", "restaurant" to "restaurant", "cinema" to "సినిమా",
        "theatre" to "సినిమా", "party" to "party", "meeting" to "meeting",
        "exam" to "exam", "test" to "test", "homework" to "homework",
        "lunch" to "lunch", "dinner" to "dinner", "breakfast" to "breakfast",
        "coffee" to "coffee", "tea" to "tea", "phone" to "phone",
        "bike" to "bike", "cab" to "cab", "auto" to "auto", "ticket" to "టికెట్",
        "bill" to "bill", "price" to "ధర", "problem" to "problem",
        "gift" to "గిఫ్ట్", "friend" to "friend", "brother" to "brother",
        "sister" to "sister", "mother" to "మమ్మీ", "father" to "నాన్న",
        "parents" to "parents", "room" to "గది", "key" to "కీ", "bag" to "బ్యాగ్",
    )

    // Style transforms: convert casual 2nd-person forms into polite/formal.
    private val politeTransforms = listOf(
        "ఉన్నావా" to "ఉన్నారా",
        "చేస్తున్నావా" to "చేస్తున్నారా",
        "వెళ్తున్నావా" to "వెళ్తున్నారా",
        "వస్తున్నావా" to "వస్తున్నారా",
        "చూస్తున్నావా" to "చూస్తున్నారా",
        "తింటున్నావా" to "తింటున్నారా",
        "చదువుతున్నావా" to "చదువుతున్నారా",
        "ఉన్నావు" to "ఉన్నారు",
        "చేస్తున్నావు" to "చేస్తున్నారు",
        "వెళ్తున్నావు" to "వెళ్తున్నారు",
        "వస్తున్నావు" to "వస్తున్నారు",
        "చూస్తున్నావు" to "చూస్తున్నారు",
        "తిన్నావా" to "తిన్నారా",
        "తిన్నావు" to "తిన్నారు",
        "వచ్చావా" to "వచ్చారా",
        "వచ్చావు" to "వచ్చారు",
        "చూశావా" to "చూశారా",
        "చూశావు" to "చూశారు",
        "చేశావా" to "చేశారా",
        "చేశావు" to "చేశారు",
        "వెళ్ళావా" to "వెళ్ళారా",
        "వెళ్ళావు" to "వెళ్ళారు",
        "అయ్యావు" to "అయ్యారు",
        "ఉంటున్నావు" to "ఉంటున్నారు",
        "చెయ్యి" to "చేయండి",
        "ఆగు" to "ఆగండి",
        "చూడు" to "చూడండి",
        "విను" to "వినండి",
        "చెప్పు" to "చెప్పండి",
        "రా" to "రండి",
        "నువ్వు" to "మీరు",
        "నీకు" to "మీకు",
        "నీ " to "మీ ",
        "నీ" to "మీ",
    )

    // Verb stems for light conjugation in structured fallback templates.
    private val continuousVerbs = mapOf(
        "go" to "వెళ్తు", "come" to "వస్తు", "eat" to "తింటు", "do" to "చేస్తు",
        "see" to "చూస్తు", "work" to "పని చేస్తు", "play" to "ఆడుతు",
        "sleep" to "నిద్రపోతు", "talk" to "మాట్లాడుతు", "study" to "చదువుతు",
        "wait" to "ఆగుతు", "walk" to "నడుస్తు", "run" to "పరుగెత్తుతు",
        "read" to "చదువుతు", "write" to "రాస్తు", "watch" to "చూస్తు",
        "listen" to "వింటు", "cook" to "వంట చేస్తు", "drink" to "తాగుతు",
        "help" to "సహాయం చేస్తు", "learn" to "నేర్చుకుంటు", "teach" to "చెప్పుతు",
        "sing" to "పాడుతు", "dance" to "డ్యాన్స్ చేస్తు", "drive" to "డ్రైవ్ చేస్తు",
        "travel" to "ప్రయాణం చేస్తు", "meet" to "కలుస్తు", "call" to "call చేస్తు",
        "think" to "అనుకుంటు", "feel" to "ఫీల్ అవుతు", "wear" to "వేసుకుంటు",
        "buy" to "కొంటు", "sell" to "అమ్ముతు", "take" to "తీసుకుంటు",
        "give" to "ఇస్తు", "get" to "తెచ్చుకుంటు", "make" to "చేస్తు",
        "tell" to "చెప్పుతు", "say" to "చెప్పుతు", "ask" to "అడుగుతు",
        "know" to "తెలుసుకుంటు", "find" to "వెతుకుతు", "start" to "మొదలుపెడుతు",
        "finish" to "పూర్తి చేస్తు", "open" to "తెరుస్తు", "close" to "మూస్తు",
        "sit" to "కూర్చుంటు", "stand" to "నిలబడుతు", "fly" to "ఎగురుతు",
        "swim" to "ఈదుతు", "climb" to "ఎక్కుతు", "fall" to "పడుతు",
        "laugh" to "నవ్వుతు", "cry" to "ఏడుస్తు", "smile" to "నవ్వుతు",
        "shout" to "అరుస్తు", "whisper" to "గుసగుసలాడుతు", "pray" to "ప్రార్థిస్తు",
        "try" to "ప్రయత్నిస్తు", "change" to "మారుస్తు", "move" to "కదులుతు",
        "stay" to "ఉంటు", "live" to "ఉంటు", "win" to "గెలుస్తు", "lose" to "ఓడుతు",
    )

    private val futureVerbs = mapOf(
        "come" to "వస్తాను", "go" to "వెళ్తాను", "do" to "చేస్తాను",
        "see" to "చూస్తాను", "call" to "call చేస్తాను", "meet" to "కలుస్తాను",
        "tell" to "చెప్తాను", "give" to "ఇస్తాను", "take" to "తీసుకుంటాను",
        "buy" to "కొంటాను", "eat" to "తింటాను", "sleep" to "నిద్రపోతాను",
        "wait" to "ఆగుతాను", "help" to "సహాయం చేస్తాను", "talk" to "మాట్లాడతాను",
        "send" to "పంపుతాను", "bring" to "తెస్తాను", "make" to "చేస్తాను",
        "watch" to "చూస్తాను", "read" to "చదువుతాను", "write" to "రాస్తాను",
        "play" to "ఆడతాను", "study" to "చదువుతాను", "work" to "పని చేస్తాను",
        "cook" to "వంట చేస్తాను", "drink" to "తాగుతాను", "think" to "అనుకుంటాను",
        "say" to "చెప్తాను", "ask" to "అడుగుతాను", "find" to "వెతుకుతాను",
        "know" to "తెలుసుకుంటాను", "learn" to "నేర్చుకుంటాను", "teach" to "చెప్తాను",
    )

    private val pastYouVerbs = mapOf(
        "eat" to "తిన్నావా", "come" to "వచ్చావా", "go" to "వెళ్ళావా",
        "see" to "చూశావా", "do" to "చేశావా", "call" to "call చేశావా",
        "sleep" to "నిద్రపోయావా", "finish" to "పూర్తి చేశావా", "read" to "చదివావా",
        "write" to "రాశావా", "meet" to "కలిశావా", "talk" to "మాట్లాడావా",
        "watch" to "చూశావా", "listen" to "విన్నావా", "drink" to "తాగావా",
        "buy" to "కొన్నావా", "understand" to "అర్థమైందా", "get" to "వచ్చిందా",
        "play" to "ఆడావా", "study" to "చదివావా", "work" to "పని చేశావా",
    )

    override suspend fun translateEnglishToTelugu(
        text: String,
        style: TranslationStyle,
    ): TranslationResult? = withContext(Dispatchers.Default) {
        val normalized = normalize(text)
        if (normalized.isBlank()) return@withContext null

        val ruleMatch = matchRule(normalized, style)
        val telugu = ruleMatch ?: structuredFallback(normalized, style) ?: return@withContext null

        TranslationResult(
            input = text.trim(),
            telugu = telugu,
            twinglish = romanizeTelugu(telugu),
            confidence = if (ruleMatch != null) 0.92f else 0.7f,
            style = style,
        )
    }

    override fun romanizeTelugu(teluguText: String, style: RomanizationStyle): String =
        Romanizer.romanize(teluguText, style)

    // ---------- internals ----------

    /**
     * Normalize for rule matching: lowercase, collapse whitespace, and
     * expand common contractions ("i'm going home" → "i am going home")
     * so chat-style input reaches the same rules as the spelled-out forms.
     */
    private fun normalize(text: String): String {
        val cleaned = text.lowercase().trim().replace(Regex("[\\s]+"), " ")
        return cleaned.split(' ').joinToString(" ") { word ->
            CONTRACTIONS[word] ?: word
        }
    }

    private companion object {
        private val ARTICLES = setOf("a", "an", "the")

        // Longest keys first is unnecessary (keys are distinct tokens) but
        // each key must be a whole word — expansion is per whitespace token.
        private val CONTRACTIONS = mapOf(
            "i'm" to "i am", "i've" to "i have", "i'll" to "i will", "i'd" to "i would",
            "you're" to "you are", "you've" to "you have", "you'll" to "you will",
            "we're" to "we are", "we'll" to "we will", "we've" to "we have",
            "they're" to "they are", "they'll" to "they will", "they've" to "they have",
            "he's" to "he is", "he'll" to "he will", "she's" to "she is", "she'll" to "she will",
            "it's" to "it is", "that's" to "that is", "there's" to "there is",
            "what's" to "what is", "who's" to "who is", "where's" to "where is",
            "how's" to "how is", "when's" to "when is", "why's" to "why is",
            "don't" to "do not", "doesn't" to "does not", "didn't" to "did not",
            "won't" to "will not", "can't" to "cannot", "couldn't" to "could not",
            "shouldn't" to "should not", "wouldn't" to "would not",
            "isn't" to "is not", "aren't" to "are not", "wasn't" to "was not", "weren't" to "were not",
            "haven't" to "have not", "hasn't" to "has not", "hadn't" to "had not",
            "let's" to "let us",
        )
    }

    private fun matchRule(normalized: String, style: TranslationStyle): String? {
        for (rule in rules) {
            val m = rule.pattern.matchEntire(normalized) ?: continue
            var out = rule.telugu
            for (g in 1 until m.groupValues.size) {
                out = out.replace("\$$g", fillSlot(m.groupValues[g]))
            }
            return applyStyle(out, style)
        }
        return null
    }

    /**
     * Fill a captured noun/verb slot with natural Telugu: articles are
     * dropped, known conversational words are substituted (movie → సినిమా)
     * and everything else (proper nouns, code-switched words) passes through.
     */
    private fun fillSlot(slot: String): String {
        val words = slot.split(' ').filter { it.isNotBlank() }
        val filled = words.mapNotNull { w ->
            when {
                w in ARTICLES -> null
                slotWords[w] != null -> slotWords.getValue(w)
                else -> w
            }
        }
        return filled.joinToString(" ").ifBlank { slot }
    }

    private val politeVerbForms = listOf(
        "ఉన్నారు", "చేస్తున్నారు", "వెళ్తున్నారు", "వస్తున్నారు", "ఉంటున్నారు",
        "తిన్నారా", "వచ్చారా", "చూశారా", "అయ్యారు", "అర్థమైందా",
    )

    private fun applyStyle(telugu: String, style: TranslationStyle): String {
        if (style == TranslationStyle.CASUAL) return telugu
        var out = telugu
        for ((from, to) in politeTransforms) {
            out = out.replace(from, to)
        }
        if (style == TranslationStyle.FORMAL) {
            out = out.replace("ఏం", "ఏమి")
        }
        // When the sentence addresses a second person with no explicit
        // subject, polite/formal style introduces "మీరు".
        val hasSubject = out.startsWith("మీరు") || out.startsWith("నేను") || out.startsWith("మనం")
        if (!hasSubject && politeVerbForms.any { out.contains(it) }) {
            out = "మీరు $out"
        }
        return out
    }

    /**
     * A small set of structurally safe templates for common sentences that
     * the phrase bank doesn't cover verbatim. Every branch produces a
     * complete natural sentence; anything else returns null (never a
     * token-by-token hybrid).
     */
    private fun structuredFallback(normalized: String, style: TranslationStyle): String? {
        val words = normalized.split(' ')

        if (words.size in 2..5) {
            when {
                // "i will <verb>"
                words.first() == "i" && words[1] == "will" && words.size == 3 ->
                    futureVerbs[words[2]]?.let { return it }

                // "i am <verb>ing"
                words.first() == "i" && words[1] == "am" && words.size >= 3 && words.last().endsWith("ing") -> {
                    val stem = words.last().removeSuffix("ing")
                    continuousVerbs[stem]?.let { return "నేను ${it}న్నాను" }
                }

                // "you are <verb>ing"
                words.first() == "you" && words[1] == "are" && words.size >= 3 && words.last().endsWith("ing") -> {
                    val stem = words.last().removeSuffix("ing")
                    continuousVerbs[stem]?.let { return "నువ్వు ${it}న్నావు" }
                }

                // "are you <verb>ing?"
                words.first() == "are" && words[1] == "you" && words.size >= 3 && words.last().endsWith("ing") -> {
                    val stem = words.last().removeSuffix("ing")
                    continuousVerbs[stem]?.let { return "నువ్వు ${it}న్నావా?" }
                }

                // "did you <verb>?"
                words.first() == "did" && words[1] == "you" && words.size == 3 ->
                    pastYouVerbs[words[2]]?.let { return applyStyle(it, style) }
            }
        }
        return null
    }
}
