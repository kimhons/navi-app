# Navi Website

Marketing website for the Navi navigation app.

## 🌐 Live Site

**Production:** https://navi-website.manus.space

## 🛠️ Tech Stack

- **Framework:** React 19 + Vite
- **Styling:** Tailwind CSS 4
- **UI Components:** shadcn/ui
- **Routing:** Wouter
- **Deployment:** Manus Platform

## 📁 Structure

```
website/
├── client/
│   ├── public/          # Static assets
│   ├── src/
│   │   ├── pages/       # Page components
│   │   │   ├── Home.tsx        # Landing page
│   │   │   └── Download.tsx    # Download page
│   │   ├── components/  # Reusable components
│   │   ├── App.tsx      # Main app component
│   │   └── main.tsx     # Entry point
│   └── index.html
└── package.json
```

## 🚀 Local Development

```bash
cd website/client
npm install
npm run dev
```

Open http://localhost:5173

## 📦 Build

```bash
npm run build
```

Output in `client/dist/`

## 🌍 Deploy

### Option 1: Manus Platform (Current)

Already deployed at https://navi-website.manus.space

### Option 2: Vercel

```bash
cd website/client
vercel
```

### Option 3: Netlify

```bash
cd website/client
netlify deploy
```

## 📱 Features

- **Landing Page**
  - Hero section with app showcase
  - Feature highlights
  - Download buttons for iOS and Android
  - Responsive design

- **Download Page**
  - Platform-specific download links
  - App screenshots
  - Technical specifications
  - Installation instructions

## 🎨 Customization

### Update App Information

Edit `client/src/const.ts`:

```typescript
export const APP_TITLE = "Navi - Navigate Smarter";
export const APP_LOGO = "/logo.svg";
```

### Update Colors

Edit `client/src/index.css`:

```css
:root {
  --primary: #2563EB;
  --secondary: #10B981;
  /* ... */
}
```

### Update Content

- Landing page: `client/src/pages/Home.tsx`
- Download page: `client/src/pages/Download.tsx`

## 📄 License

Part of the Navi Navigation App project.
