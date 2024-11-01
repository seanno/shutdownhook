
// I can't keep all of these straight to save my life.
//
// b64u = base64url
// b64  = base64
// arr  = UInt8Array
// str  = string

export function b64u_to_arr(input) {
  return(b64_to_arr(b64u_to_b64(input)));
}

export function b64u_to_str(input) {
  return(b64_to_str(b64u_to_b64(input)));
}

export function b64_to_str(input) {
  return(arr_to_str(b64_to_arr(input)));
}

export function b64_to_arr(input) {
  const raw = atob(input);
  const arr = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; ++i) arr[i] = raw.charCodeAt(i);
  return(arr);
}

export function arr_to_str(input) {
  return(new TextDecoder().decode(input));
}

// From https://stackoverflow.com/questions/5234581/base64url-decoding-via-javascript

export function b64u_to_b64(input) {

  let b64;
  
  // Replace non-url compatible chars with base64 standard chars
  b64 = input
    .replace(/-/g, '+')
    .replace(/_/g, '/');

  // Pad out with standard base64 required padding characters
  var pad = input.length % 4;
  if (pad) {
    if(pad === 1) {
      throw new Error('InvalidLengthError: input is wrong length to determine padding');
    }
    b64 += new Array(5-pad).join('=');
  }

  return(b64);
}


