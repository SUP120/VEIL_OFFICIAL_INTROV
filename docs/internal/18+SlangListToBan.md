# 🚫 Slang & Abuse Word List — Used by ContentModerator.kt

> **Status:** Implemented in `util/ContentModerator.kt`  
> These words are auto-blocked across all chat, questions, and inputs.

---

## 🔴 English Sexual Keywords
sex, sexy, nude, nudes, naked, porn, pornography, boobs, boob, tits, tit, dick, cock, pussy, vagina, penis, anal, blowjob, handjob, orgasm, cum, cumshot, horny, milf, dildo, vibrator, fetish, bdsm, erotic, xxx, onlyfans, hookup, hook up, fwb, one night stand, smash, bang, wanna fuck, dtf, send pics, send nudes, show me, strip, sext, sexting, topless, nipple, nipples, cleavage, masturbate, masturbation, kinky, threesome, lingerie, underwear pic, body shot

---

## 🔴 Hindi Sexual Words (Pure Hindi)
chut, choot, lund, lauda, laude, gaand, gand, bhosdike, bhosdi, bhosdiwale, bhosdiwali, randi, randwa, randwe, rande, chutiya, chutiye, chutiyap, madarchod, madarchodi, behenchod, behenchodi, mc, bc, bkl, bsdk, lodu, tatte, jhant, jhaant, maa ki aankh, teri maa, teri behen, nangi, nanga, nange, chhinal, chhad, sambhog, yaun, yoni, ling, lingam, chutia, lode, bkl, mkc, mkb, mkbh, tmkc, mc, bc 

---

## 🔴 Hinglish Sexual Slang
malmal, maal, item, patao, patana, patao use, full maza, maza aayega, dheere dheere, dhakke maar, chipak, chipka, ghus, ghusna, chodna, chod, choda, chodi, chodoge, marna, thokna, thok, thoka, ghanta, ghante, bur, bhur, lene do, dene do, aao karte hain, karte hain, ek kaam karo, kaam karo, nanga kar, kapde utaro, photo bhejo, pic bhejo, pic do, video bhejo, video do, live aao, live karo

---

## 🟡 English Slurs (Hate Speech)
nigger, nigga, n1gger, n1gga, faggot, fag, f4g, tranny, retard, retarded, spic, wetback, kike, chink, gook, slant, coon, darkie, towelhead, raghead, beaner, cracker, honky, whitey, dyke, lesbo, homo, cripple, midget, mongoloid

---

## 🟡 Hindi Slurs
chamaar, chamar, bhangi, kutta, kutte, kutiya, hijra, hijde, chakka, dalla, dalli, haram, harami, haramkhor, haramzada, haramzadi, kamina, kamine, kaminey, ullu, ullu ka patha, gadha, suar, suwar, gandu, gaandu, madarjaat, nalayak, nikamma

---

## 🟡 Hinglish Abuse / Street Slang
bakwaas kar, bakwaas band kar, mooh band kar, chup kar saale, apni aukaat mein reh, teri aukaat, gali de raha, gaali, bhadwa, bhadwe, bhadwi, khanki, khankin, saali, saala, saale, kutte ki aulad, suar ki aulad, jhandu, jhand, bewakoof, pagal, pagle, idiot sala, stupid sala

---

## 🔵 Social Media Detection
instagram, insta, ig, snapchat, snap, sc, whatsapp, wtsapp, wa number, telegram, tg, tele, facebook, fb, twitter, x.com, discord, tiktok, kik, hike, bumble, tinder, hinge, youtube, yt channel, @username patterns, dm me, add me, follow me, text me, ping me, my id, mera id, apna id, id do, id dena, id loge

---

## 🔵 Location / Money
address, location, where do you live, which city, which area, pin code, pincode, zip code, ghar ka address, address bata, kahan rehti, kahan rehte, send money, paytm, gpay, google pay, phonepe, upi, bank account, loan, paise bhejo, paise chahiye, meet me, come to my place, hotel, room number, coffee pe chalogi, milne chaloge, akele miloge, ghar pe aa, ghar bulao

---

## 🔵 Dating Pressure
be my girlfriend, be my boyfriend, date me, i love you, marry me, single?, are you single, committed, meri girlfriend ban, mera boyfriend ban, kya tum single ho, pyaar karte ho, rishta karna hai

---

## 🔵 Age Fishing / Minor Detection
how old are you, what is your age, kitne saal, tumhari umar, teri umar, age bata, are you a minor, are you 18, less than 18, below 18, under 18, school mein ho, college mein ho, baccha hai, chhota hai, chhoti

---

## 📝 Implementation Notes
- Implemented in `util/ContentModerator.kt`
- Short words (≤3 chars) use word-boundary matching to avoid false positives
- Phrases (with spaces) use substring matching
- All other words use `.contains()` to catch leet-speak variants
- Categories: `phone_number`, `social_media`, `sexual`, `hate_speech`, `location_money`, `dating_pressure`, `age_fishing`
- Violations feed into `StrikeManager.kt` (1st = warn, 2nd = 24h temp ban, 3rd = permanent ban)