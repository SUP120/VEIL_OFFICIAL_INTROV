// Test script: verify our token algorithm against the official agora-token npm package
// Run: node test_token.js
const { RtcTokenBuilder, RtcRole } = require('agora-token');

const APP_ID          = '8340cd549dbe4d19ba8fa3a70a5b6686';
const APP_CERTIFICATE = '6096917f715541a69c844d8e3a59f261';
const CHANNEL_NAME    = 'V5Fhd8pGBB92slYelSlV'; // from logcat
const UID             = 425726283;               // from logcat
const TOKEN_EXPIRE    = 3600;
const PRIV_EXPIRE     = Math.floor(Date.now() / 1000) + TOKEN_EXPIRE;

const token = RtcTokenBuilder.buildTokenWithUid(
    APP_ID, APP_CERTIFICATE, CHANNEL_NAME, UID,
    RtcRole.PUBLISHER, PRIV_EXPIRE, TOKEN_EXPIRE
);

console.log('Reference token (agora-token npm):');
console.log('  Full  :', token);
console.log('  Prefix:', token.substring(0, 40));
console.log('  Length:', token.length);

// Also try with uid=0 (some Agora configs use 0 for any user)
const token0 = RtcTokenBuilder.buildTokenWithUid(
    APP_ID, APP_CERTIFICATE, CHANNEL_NAME, 0,
    RtcRole.PUBLISHER, PRIV_EXPIRE, TOKEN_EXPIRE
);
console.log('\nReference token with uid=0:');
console.log('  Prefix:', token0.substring(0, 40));
console.log('  Length:', token0.length);
