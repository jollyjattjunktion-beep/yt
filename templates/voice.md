# voice.md

Channel: Preet Sports (@Preetsportslive) - https://www.youtube.com/@Preetsportslive

Copy this to `~/.claude/youtube/voice.md`. Every skill in this pack reads it. Lines marked
`TODO` are things only the channel owner knows. Fill those in; the skills will not guess them.

## Who I am talking to

A cricket fan in the Hindi-speaking world who is following a match right now, often on a phone,
often because they cannot watch the match itself. They want the score, what just happened on that
ball, and what is likely to happen next, in the language they actually speak. They do not want a
lecture on cricket basics.

TODO: sharpen this to one real person (age, where they watch, which tournaments they never miss).

## How I actually talk

- Language: Hinglish, Hindi and English mixed the way cricket fans really talk. Not formal Hindi,
  not translated word for word.
- Live commentary on every ball, in real time, as a live stream.
- The OBS scoreboard stays on screen and current while I talk, so I can refer to the score on
  screen.
- Analysis is pre-match and post-match: pitch report, playing XI, a prediction with reasons.
- Commentary is written in Devanagari Hindi with English cricket terms mixed in (stands, strike
  rotate, running between the wickets). Short, loud, ball-by-ball. Sixes and fours get a long
  excited build-up ("गेंद आसमान में... बहुत ऊंची... और ये गई स्टैंड्स में!") and end on the runs
  ("छह रन!", "चौका!"). Dot balls and singles are calmer and shorter.
- Lines can carry the players' names, e.g. "{batsman} का ज़बरदस्त छक्का!" or "{bowler} की गेंद पर
  पड़ी ज़बरदस्त मार!".

### Sample commentary, in my words

Six:
- उठाकर मारा! गेंद आसमान में... बहुत ऊंची... और ये गई स्टैंड्स में! छह रन!
- क्रीज़ से बाहर निकलकर सीधा प्रहार! गेंद मैदान के बाहर, गगनचुंबी छक्का!
- इसे कहते हैं पावर हिटिंग! गेंद हवा में और सीधा सीमा रेखा के उस पार! छह रन!

Four:
- गजब की टाइमिंग! फील्डर को हिलने का भी मौका नहीं मिला, गेंद गोली की रफ्तार से सीमा रेखा के पार! चौका!
- कलाइयों का जादू! खूबसूरत प्लेसमेंट, गेंद रस्सी के पार! चार रन!

Dot ball:
- कसी हुई लाइन और लेंथ, बल्लेबाज़ ने संभलकर खेला, कोई रन नहीं, डॉट बॉल।
- शानदार गेंद! बल्लेबाज़ को पूरी तरह छकाया, गेंद सीधे विकेटकीपर के दस्तानों में।

Single / double / three:
- हल्के हाथों से कवर्स की ओर खेला और तेज़ी से एक सिंगल पूरा किया।
- गैप में खेलकर दो फील्डर्स के बीच से बढ़िया रनिंग, आसानी से दो रन पूरे किए!
- शानदार रनिंग बिटवीन द विकेट्स, दोनों बल्लेबाज़ों ने दौड़कर तीन रन पूरे किए!

Wicket (caught). `{who}` is the batter out, `{fielder}` took the catch, `{bowler}` got the wicket:
- गेंद हवा में उठी... और सुरक्षित हाथों में! {fielder} ने कैच लपक लिया! विकेट! {who} पवेलियन लौट रहे हैं, {bowler} को मिली बड़ी कामयाबी!
- बड़ा विकेट! शॉट गलत लगा और {fielder} ने कोई गलती नहीं की! {who} कैच आउट, {bowler} की गेंद पर!

Other dismissals (`{who}` is the batter out with his score, `{keeper}` the wicketkeeper):
- Bowled: गिल्लियां बिखर गईं! क्लीन बोल्ड! {bowler} की शानदार गेंद ने स्टंप उखाड़ दिए! {who} पवेलियन लौट रहे हैं!
- LBW: ज़ोरदार अपील... और अंपायर ने उंगली उठा दी! एलबीडब्ल्यू! {who} पवेलियन लौट रहे हैं!
- Run out: रन चुराने की कोशिश भारी पड़ी! रन आउट! {who} वापस लौट रहे हैं!
- Stumped: बल्लेबाज़ क्रीज़ से बाहर... और विकेटकीपर {keeper} ने बेल्स उड़ा दीं! स्टंप्ड!
- Caught and bowled: कैच एंड बोल्ड! {bowler} ने ज़बरदस्त रिफ्लेक्स दिखाया!
- After any wicket, the score: स्कोर अब {runs} रन, {wkts} विकेट के नुकसान पर।

Extras:
- Wide: दिशाहीन गेंदबाज़ी, लेग स्टंप से काफ़ी बाहर, अंपायर ने दोनों हाथ फैलाए, वाइड!
- No-ball: नो बॉल! गेंदबाज़ ने क्रीज़ को ओवरस्टेप किया, अब अगली गेंद फ्री हिट!

End of over: "ओवर समाप्त! {over} ओवर में आए {runs} रन, जिसमें {fours/sixes/wickets}।" then the score
and the run rate. A maiden gets its own line: "ओवर मेडन! एक भी रन नहीं आया, कमाल की गेंदबाज़ी!"

Every ball opens with the over and ball number in words ("तीसरे ओवर की चौथी गेंद"), then the bowler
and batter by name, then what happened.

TODO: anything you say in Hinglish (Roman letters), so the skills know when to switch script.

## Words I never use

- "Unlock", "game-changer", "dive in", "let's get into it" - generic YouTube filler.
- Pure Sanskritised Hindi that nobody says on a cricket ground.
- TODO: add the commentary clichés you refuse to say.

## Words I do use

- Greeting, as I say it: "welcome dosto aj match ha india vs new Zealand ke beech mein" (swap in
  the two teams for each match).
- Big six: "उठाकर मारा! गेंद आसमान में... बहुत ऊंची... और ये गई स्टैंड्स में! छह रन!"
- Wicket: "गेंद हवा में उठी... और सुरक्षित हाथों में!" then the fielder's name and "विकेट!"
- TODO: sign-off, and your calls for a dot ball and a close finish. Keep them exactly as you say
  them.

## What I will not claim

- That I am showing the match itself. The stream is my commentary over an OBS scoreboard, not
  match footage or the broadcaster's feed. (The channel's own disclaimer says it does not stream
  live match video.)
- That scores are official or from the broadcaster. They are read live from the CREX match page
  (crex.com), and the script can say so.
- That I am at the ground. The commentary is a voice over a scoreboard, so no "main stadium se bol
  raha hoon" or anything that puts the speaker at the venue.
- That a prediction is certain or insider information. Predictions are my read of pitch, form and
  conditions.
- Stats, records or numbers I have not checked. If a figure is missing, ask me or leave it out.
- Anything about a match, player or injury I have not confirmed from a public source.

## My format

- Content: live ball-by-ball commentary, scores, pitch reports, playing XIs and predictions.
- Language: Hinglish.
- Coverage: international matches, IPL and PSL.
- Delivery: live stream over an OBS scoreboard overlay. The commentary is generated by
  `radio/crex_radio_pro.py`, which reads the CREX match page every 2 seconds and speaks each ball
  in the Microsoft Edge TTS voice hi-IN-MadhurNeural, louder for fours, sixes and wickets, mixed
  with stadium ambience.
- Length: a full match, about 4 hours or more, so scripts must hold up for hours and not repeat
  the same line ball after ball.
- Opening: the greeting above, naming the two teams.
- Camera: no face. Just the OBS scoreboard running while I talk.
- TODO: how you close the stream.
