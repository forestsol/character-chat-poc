import type { Config } from "tailwindcss";

export default {
  content: ["./app/**/*.{js,ts,jsx,tsx,mdx}", "./components/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      colors: {
        ink: "#19221d",
        paper: "#f6f2e8",
        moss: "#526a58",
        copper: "#b66a3c"
      },
      boxShadow: {
        card: "0 18px 50px rgba(40, 49, 42, 0.10)"
      }
    }
  },
  plugins: []
} satisfies Config;
