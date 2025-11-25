import { Navigation } from "lucide-react";
import { APP_TITLE } from "@/const";
import { Link } from "wouter";

export default function Privacy() {
  return (
    <div className="min-h-screen bg-background">
      {/* Navigation */}
      <nav className="fixed top-0 left-0 right-0 z-50 bg-background/80 backdrop-blur-xl border-b border-border/50">
        <div className="container mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <Link href="/">
              <div className="flex items-center space-x-2 cursor-pointer">
                <Navigation className="w-8 h-8 text-primary" />
                <span className="text-2xl font-bold bg-gradient-to-r from-[oklch(var(--gradient-from))] to-[oklch(var(--gradient-to))] bg-clip-text text-transparent">
                  {APP_TITLE}
                </span>
              </div>
            </Link>
          </div>
        </div>
      </nav>

      {/* Content */}
      <div className="pt-32 pb-20">
        <div className="container mx-auto px-6 max-w-4xl">
          <h1 className="text-4xl md:text-5xl font-bold mb-4">Privacy Policy</h1>
          <p className="text-foreground/60 mb-8">
            Effective Date: November 17, 2025 | Last Updated: November 17, 2025
          </p>

          <div className="prose prose-lg max-w-none">
            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Introduction</h2>
              <p className="text-foreground/80 leading-relaxed mb-4">
                Welcome to Navi. We are committed to protecting your privacy and ensuring you have a positive experience when using our navigation application. This Privacy Policy explains how we collect, use, disclose, and safeguard your information when you use our mobile application and related services.
              </p>
              <p className="text-foreground/80 leading-relaxed">
                By using Navi, you agree to the collection and use of information in accordance with this policy. If you do not agree with the terms of this Privacy Policy, please do not access or use the application.
              </p>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Information We Collect</h2>
              
              <h3 className="text-2xl font-semibold mb-3 mt-6">1. Location Data</h3>
              <p className="text-foreground/80 leading-relaxed mb-4">
                Navi is a navigation application that fundamentally relies on your location information to provide turn-by-turn directions, show your position on the map, and deliver location-based features. Location data is essential to the core functionality of our service.
              </p>
              
              <div className="bg-muted/30 p-6 rounded-lg mb-6">
                <h4 className="font-semibold mb-3">Types of Location Data We Collect:</h4>
                <ul className="space-y-2 text-foreground/80">
                  <li>• Real-time GPS coordinates for navigation guidance</li>
                  <li>• Location history during active navigation sessions</li>
                  <li>• Saved locations (home, work, favorites)</li>
                  <li>• Parking locations you choose to save</li>
                </ul>
              </div>

              <h4 className="font-semibold mb-3">How We Use Location Data:</h4>
              <p className="text-foreground/80 leading-relaxed mb-4">
                Your location information is used exclusively to provide navigation services including turn-by-turn navigation, route calculation, traffic information, nearby search, offline navigation, parking reminders, and trip analytics.
              </p>

              <h4 className="font-semibold mb-3">Location Permissions:</h4>
              <p className="text-foreground/80 leading-relaxed mb-4">
                <strong>"When In Use":</strong> Allows Navi to access your location only while actively using the app.
                <br />
                <strong>"Always":</strong> Enables continuous navigation even when the app is in the background (optional but recommended).
              </p>

              <h3 className="text-2xl font-semibold mb-3 mt-8">2. Mapbox Integration</h3>
              <p className="text-foreground/80 leading-relaxed mb-4">
                Navi uses Mapbox as our mapping provider to deliver high-quality maps and offline navigation capabilities. When you download offline maps or use navigation features, certain data is processed by Mapbox.
              </p>

              <div className="bg-primary/10 p-6 rounded-lg mb-6 border border-primary/20">
                <h4 className="font-semibold mb-3">What Mapbox Collects:</h4>
                <ul className="space-y-2 text-foreground/80">
                  <li>• Map tile requests when viewing maps</li>
                  <li>• Routing requests when calculating directions</li>
                  <li>• Search queries for location searches</li>
                  <li>• Device information and IP address</li>
                </ul>
                <p className="text-sm text-foreground/60 mt-4">
                  Mapbox does not sell your personal data. For complete details, see the{" "}
                  <a href="https://www.mapbox.com/legal/privacy" target="_blank" rel="noopener noreferrer" className="text-primary hover:underline">
                    Mapbox Privacy Policy
                  </a>.
                </p>
              </div>

              <h4 className="font-semibold mb-3">Offline Maps:</h4>
              <p className="text-foreground/80 leading-relaxed mb-4">
                When you download offline maps, the map data is stored locally on your device. No location data is transmitted to Mapbox or our servers while you navigate offline. Offline navigation works entirely on your device without any network connection.
              </p>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">How We Use Your Information</h2>
              <div className="grid md:grid-cols-2 gap-4 mb-6">
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Core Services</h4>
                  <p className="text-sm text-foreground/70">Calculate routes, provide directions, show traffic, enable offline navigation</p>
                </div>
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Personalization</h4>
                  <p className="text-sm text-foreground/70">Remember saved locations, recent destinations, and preferences</p>
                </div>
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Social Features</h4>
                  <p className="text-sm text-foreground/70">Connect with friends, share location, coordinate meetups</p>
                </div>
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Improvements</h4>
                  <p className="text-sm text-foreground/70">Analyze usage patterns, identify bugs, develop new features</p>
                </div>
              </div>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Data Sharing</h2>
              <div className="bg-destructive/10 p-6 rounded-lg mb-6 border border-destructive/20">
                <h3 className="text-xl font-bold mb-2">We Do Not Sell Your Data</h3>
                <p className="text-foreground/80">
                  We do not sell, rent, or trade your personal information to third parties for marketing purposes. Your location data and personal information are never sold to advertisers or data brokers.
                </p>
              </div>

              <h4 className="font-semibold mb-3">Third-Party Service Providers:</h4>
              <ul className="space-y-3 text-foreground/80 mb-6">
                <li><strong>Mapbox:</strong> Processes location data to provide maps and navigation</li>
                <li><strong>Cloud Infrastructure:</strong> Secure hosting for account data and backups</li>
                <li><strong>Analytics:</strong> Privacy-focused analytics tools with anonymized data</li>
              </ul>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Your Privacy Rights</h2>
              <div className="grid md:grid-cols-2 gap-4">
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Access & Correction</h4>
                  <p className="text-sm text-foreground/70">View and update your information anytime</p>
                </div>
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Deletion</h4>
                  <p className="text-sm text-foreground/70">Delete your account and all data permanently</p>
                </div>
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Data Export</h4>
                  <p className="text-sm text-foreground/70">Export your data in multiple formats</p>
                </div>
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Opt-Out</h4>
                  <p className="text-sm text-foreground/70">Disable analytics and marketing communications</p>
                </div>
              </div>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Data Security</h2>
              <p className="text-foreground/80 leading-relaxed mb-4">
                We implement industry-standard security measures including encrypted transmission (TLS), encrypted storage, access controls, and secure infrastructure. Most of your data is stored locally on your device where you have full control.
              </p>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Contact Us</h2>
              <p className="text-foreground/80 leading-relaxed mb-4">
                If you have questions about this Privacy Policy, please contact us:
              </p>
              <div className="bg-muted/30 p-6 rounded-lg">
                <p className="mb-2"><strong>Email:</strong> privacy@navi.app</p>
                <p className="mb-2"><strong>Support:</strong> https://navi.app/support</p>
                <p className="text-sm text-foreground/60 mt-4">
                  We will respond to your inquiry within 30 days.
                </p>
              </div>
            </section>

            <div className="bg-primary/10 p-6 rounded-lg border border-primary/20">
              <h3 className="text-xl font-bold mb-3">Summary</h3>
              <div className="grid md:grid-cols-2 gap-4 text-sm">
                <div>
                  <p className="font-semibold mb-2">What We Collect:</p>
                  <ul className="space-y-1 text-foreground/70">
                    <li>• Location data for navigation</li>
                    <li>• Account information</li>
                    <li>• Usage data and analytics</li>
                  </ul>
                </div>
                <div>
                  <p className="font-semibold mb-2">Your Rights:</p>
                  <ul className="space-y-1 text-foreground/70">
                    <li>• Access your data</li>
                    <li>• Delete your account</li>
                    <li>• Export your data</li>
                    <li>• Control location sharing</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Footer */}
      <footer className="bg-muted/30 py-8 border-t border-border">
        <div className="container mx-auto px-6 text-center">
          <p className="text-sm text-foreground/60">
            © 2025 {APP_TITLE}. All rights reserved.
          </p>
        </div>
      </footer>
    </div>
  );
}
