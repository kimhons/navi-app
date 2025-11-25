import { Navigation } from "lucide-react";
import { APP_TITLE } from "@/const";
import { Link } from "wouter";

export default function Terms() {
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
          <h1 className="text-4xl md:text-5xl font-bold mb-4">Terms of Service</h1>
          <p className="text-foreground/60 mb-8">
            Effective Date: November 17, 2025 | Last Updated: November 17, 2025
          </p>

          <div className="prose prose-lg max-w-none">
            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Agreement to Terms</h2>
              <p className="text-foreground/80 leading-relaxed mb-4">
                Welcome to Navi. These Terms of Service constitute a legally binding agreement between you and Navi regarding your use of our mobile application and related services. By downloading, installing, or using Navi, you agree to be bound by these Terms.
              </p>
              <div className="bg-destructive/10 p-4 rounded-lg border border-destructive/20">
                <p className="text-foreground/80 font-medium">
                  If you do not agree to these Terms, do not use Navi.
                </p>
              </div>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Description of Service</h2>
              <p className="text-foreground/80 leading-relaxed mb-6">
                Navi is a mobile navigation application that provides turn-by-turn directions, offline maps, real-time traffic information, and social features to help you navigate efficiently and safely.
              </p>

              <div className="grid md:grid-cols-2 gap-4 mb-6">
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Core Navigation</h4>
                  <p className="text-sm text-foreground/70">Real-time GPS navigation with voice guidance and traffic updates</p>
                </div>
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Offline Maps</h4>
                  <p className="text-sm text-foreground/70">Download regions for navigation without internet connection</p>
                </div>
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Social Features</h4>
                  <p className="text-sm text-foreground/70">Share location, message friends, coordinate meetups</p>
                </div>
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Advanced Features</h4>
                  <p className="text-sm text-foreground/70">Multi-stop routing, speed cameras, parking, analytics</p>
                </div>
              </div>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Safe Driving Responsibilities</h2>
              <div className="bg-yellow-500/10 p-6 rounded-lg border border-yellow-500/20 mb-6">
                <h3 className="text-xl font-bold mb-3 text-yellow-700 dark:text-yellow-500">⚠️ Important Safety Notice</h3>
                <p className="text-foreground/80 leading-relaxed mb-4">
                  Navi is designed to assist with navigation, but it does not replace your responsibility as a driver to operate your vehicle safely and in compliance with all applicable laws.
                </p>
              </div>

              <h4 className="font-semibold mb-3">You Are Responsible For:</h4>
              <ul className="space-y-2 text-foreground/80 mb-6">
                <li>• <strong>Obeying all traffic laws</strong> - Signs, signals, speed limits, and road markings take precedence over app directions</li>
                <li>• <strong>Staying alert</strong> - Do not let Navi distract you from the road</li>
                <li>• <strong>Using hands-free</strong> - Comply with local laws regarding device use while driving</li>
                <li>• <strong>Verifying directions</strong> - Use your own judgment to ensure routes are safe and appropriate</li>
              </ul>

              <div className="bg-destructive/10 p-4 rounded-lg border border-destructive/20">
                <p className="text-foreground/80 font-medium">
                  <strong>No Liability for Accidents:</strong> We are not liable for any accidents, injuries, property damage, traffic violations, or other consequences resulting from your use of Navi while driving.
                </p>
              </div>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Location Data and Privacy</h2>
              <p className="text-foreground/80 leading-relaxed mb-4">
                Your use of Navi involves the collection and processing of location data as described in our{" "}
                <Link href="/privacy">
                  <span className="text-primary hover:underline cursor-pointer">Privacy Policy</span>
                </Link>
                . By using Navi, you consent to the collection, use, and sharing of your location data as outlined in the Privacy Policy.
              </p>

              <h4 className="font-semibold mb-3">Mapbox Integration:</h4>
              <p className="text-foreground/80 leading-relaxed mb-4">
                Navi uses Mapbox for mapping and navigation services. Your use of Navi is also subject to Mapbox's Terms of Service and Privacy Policy. By using Navi, you agree to comply with Mapbox's terms.
              </p>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Offline Maps</h2>
              <div className="bg-muted/30 p-6 rounded-lg mb-6">
                <h4 className="font-semibold mb-3">Storage Limits:</h4>
                <ul className="space-y-2 text-foreground/80">
                  <li>• <strong>Free users:</strong> 1 offline map region</li>
                  <li>• <strong>Premium users:</strong> Unlimited regions (5GB total)</li>
                </ul>
              </div>

              <p className="text-foreground/80 leading-relaxed mb-4">
                <strong>Map Updates:</strong> Offline maps may become outdated as roads and features change. We recommend updating your offline maps regularly. We are not responsible for navigation errors resulting from outdated offline maps.
              </p>

              <p className="text-foreground/80 leading-relaxed">
                <strong>Mapbox Terms:</strong> Offline maps are provided by Mapbox and subject to Mapbox's licensing terms. You may not extract, redistribute, or use offline map data outside of Navi.
              </p>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Subscriptions and Payments</h2>
              
              <div className="grid md:grid-cols-3 gap-4 mb-6">
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Free</h4>
                  <p className="text-2xl font-bold mb-2">$0</p>
                  <p className="text-sm text-foreground/70">Basic navigation, 1 offline region</p>
                </div>
                <div className="bg-primary/10 p-4 rounded-lg border border-primary">
                  <h4 className="font-semibold mb-2">Premium</h4>
                  <p className="text-2xl font-bold mb-2">$4.99/mo</p>
                  <p className="text-sm text-foreground/70">Unlimited offline maps, advanced features</p>
                </div>
                <div className="bg-card p-4 rounded-lg border border-border">
                  <h4 className="font-semibold mb-2">Pro</h4>
                  <p className="text-2xl font-bold mb-2">$9.99/mo</p>
                  <p className="text-sm text-foreground/70">API access, fleet management</p>
                </div>
              </div>

              <h4 className="font-semibold mb-3">Billing and Renewal:</h4>
              <p className="text-foreground/80 leading-relaxed mb-4">
                Subscriptions are billed through your Apple App Store account and automatically renew unless canceled at least 24 hours before the end of the current billing period. You can manage subscriptions in your App Store account settings.
              </p>

              <p className="text-foreground/80 leading-relaxed">
                <strong>Refunds:</strong> All subscription fees are non-refundable except as required by law. Contact support@navi.app within 30 days if you believe you were charged in error.
              </p>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Disclaimers</h2>
              <div className="bg-muted/30 p-6 rounded-lg mb-6">
                <p className="text-foreground/80 leading-relaxed mb-4">
                  <strong>NO WARRANTY:</strong> NAVI IS PROVIDED "AS IS" AND "AS AVAILABLE" WITHOUT WARRANTIES OF ANY KIND. We do not warrant that Navi will be uninterrupted, error-free, or that maps and routing information will be accurate.
                </p>
                <p className="text-foreground/80 leading-relaxed mb-4">
                  <strong>NAVIGATION ACCURACY:</strong> While we strive to provide accurate navigation, we do not guarantee that routes, maps, or traffic data will be current or complete. Road conditions change constantly.
                </p>
                <p className="text-foreground/80 leading-relaxed">
                  <strong>NO EMERGENCY SERVICES:</strong> Navi is not a substitute for emergency services. In case of emergency, contact local emergency services immediately.
                </p>
              </div>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Limitation of Liability</h2>
              <div className="bg-destructive/10 p-6 rounded-lg border border-destructive/20">
                <p className="text-foreground/80 leading-relaxed mb-4">
                  TO THE MAXIMUM EXTENT PERMITTED BY LAW, NAVI SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES ARISING OUT OF YOUR USE OF NAVI.
                </p>
                <p className="text-foreground/80 leading-relaxed">
                  IN NO EVENT SHALL OUR TOTAL LIABILITY EXCEED THE AMOUNT YOU PAID IN THE 12 MONTHS PRECEDING THE CLAIM, OR $100, WHICHEVER IS GREATER.
                </p>
              </div>

              <h4 className="font-semibold mb-3 mt-6">We Are Not Liable For:</h4>
              <ul className="space-y-2 text-foreground/80">
                <li>• Accidents, injuries, or property damage while using Navi</li>
                <li>• Navigation errors, wrong turns, or delays</li>
                <li>• Service interruptions or outages</li>
                <li>• Data loss or device issues</li>
                <li>• Third-party actions or omissions</li>
              </ul>
            </section>

            <section className="mb-12">
              <h2 className="text-3xl font-bold mb-4">Contact Us</h2>
              <p className="text-foreground/80 leading-relaxed mb-4">
                If you have questions about these Terms, please contact us:
              </p>
              <div className="bg-muted/30 p-6 rounded-lg">
                <p className="mb-2"><strong>Email:</strong> legal@navi.app</p>
                <p className="mb-2"><strong>Support:</strong> https://navi.app/support</p>
              </div>
            </section>

            <div className="bg-primary/10 p-6 rounded-lg border border-primary/20">
              <p className="text-foreground/80 font-medium text-center">
                BY USING NAVI, YOU ACKNOWLEDGE THAT YOU HAVE READ, UNDERSTOOD, AND AGREE TO BE BOUND BY THESE TERMS OF SERVICE.
              </p>
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
