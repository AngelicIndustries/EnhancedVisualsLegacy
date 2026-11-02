#version 120
uniform sampler2D DiffuseSampler;
uniform float Saturation;
varying vec2 texCoord;
void main() {
    vec3 color = texture2D(DiffuseSampler, texCoord).rgb;
    float grey = dot(color, vec3(0.299, 0.587, 0.114));
    gl_FragColor = vec4(mix(vec3(grey), color, Saturation), 1.0);
}
