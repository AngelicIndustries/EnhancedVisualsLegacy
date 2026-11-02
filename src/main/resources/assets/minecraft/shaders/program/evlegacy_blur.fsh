#version 120
uniform sampler2D DiffuseSampler;
uniform vec2 BlurDir;
uniform float Radius;
varying vec2 texCoord;
varying vec2 oneTexel;
void main() {
    vec2 offset = oneTexel * BlurDir;
    float radius = max(Radius, 0.0);
    float whole = floor(radius);
    float fraction = radius - whole;
    vec3 blurred = texture2D(DiffuseSampler, texCoord).rgb;
    for (float r = 1.0; r <= whole; r += 1.0) {
        blurred += texture2D(DiffuseSampler, texCoord + offset * r).rgb;
        blurred += texture2D(DiffuseSampler, texCoord - offset * r).rgb;
    }
    blurred += fraction * (texture2D(DiffuseSampler, texCoord + offset * (whole + 1.0)).rgb
                        + texture2D(DiffuseSampler, texCoord - offset * (whole + 1.0)).rgb);
    gl_FragColor = vec4(blurred / (2.0 * radius + 1.0), 1.0);
}
