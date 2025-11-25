import { useAuth } from "@/_core/hooks/useAuth";
import { Button } from "@/components/ui/button";
import { APP_TITLE } from "@/const";
import { 
  Navigation, 
  MapPin, 
  Wifi, 
  MessageCircle, 
  TrendingUp, 
  Camera,
  Car,
  Download,
  Star,
  Users,
  Zap,
  Shield,
  ArrowRight,
  Check
} from "lucide-react";
import { useEffect, useRef } from "react";

export default function Home() {
  // The userAuth hooks provides authentication state
  // To implement login/logout functionality, simply call logout() or redirect to getLoginUrl()
  let { user, loading, error, isAuthenticated, logout } = useAuth();

  const heroRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("animate-fade-in-up");
          }
        });
      },
      { threshold: 0.1 }
    );

    document.querySelectorAll(".animate-on-scroll").forEach((el) => {
      observer.observe(el);
    });

    return () => observer.disconnect();
  }, []);

  return (
    <div className="min-h-screen bg-background">
      {/* Navigation */}
      <nav className="fixed top-0 left-0 right-0 z-50 bg-background/80 backdrop-blur-xl border-b border-border/50">
        <div className="container mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <Navigation className="w-8 h-8 text-primary" />
              <span className="text-2xl font-bold bg-gradient-to-r from-[oklch(var(--gradient-from))] to-[oklch(var(--gradient-to))] bg-clip-text text-transparent">
                {APP_TITLE}
              </span>
            </div>
            <div className="hidden md:flex items-center space-x-8">
              <a href="#features" className="text-foreground/70 hover:text-foreground transition-colors">Features</a>
              <a href="#pricing" className="text-foreground/70 hover:text-foreground transition-colors">Pricing</a>
              <a href="#testimonials" className="text-foreground/70 hover:text-foreground transition-colors">Reviews</a>
              <Button className="bg-gradient-to-r from-[oklch(var(--gradient-from))] to-[oklch(var(--gradient-to))] text-white hover:opacity-90 transition-opacity">
                Download Now
              </Button>
            </div>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section ref={heroRef} className="relative pt-32 pb-20 overflow-hidden">
        {/* Gradient Background */}
        <div className="absolute inset-0 bg-gradient-to-br from-[oklch(var(--gradient-from))] via-[oklch(var(--gradient-to))] to-background opacity-10"></div>
        
        <div className="container mx-auto px-6 relative z-10">
          <div className="max-w-4xl mx-auto text-center">
            {/* Badge */}
            <div className="inline-flex items-center space-x-2 bg-primary/10 text-primary px-4 py-2 rounded-full mb-8 animate-on-scroll">
              <Zap className="w-4 h-4" />
              <span className="text-sm font-medium">6 Powerful New Features Added</span>
            </div>

            {/* Headline */}
            <h1 className="text-5xl md:text-7xl font-bold mb-6 leading-tight animate-on-scroll">
              Navigate Smarter.
              <br />
              <span className="bg-gradient-to-r from-[oklch(var(--gradient-from))] to-[oklch(var(--gradient-to))] bg-clip-text text-transparent">
                Arrive Happier.
              </span>
            </h1>

            {/* Subheadline */}
            <p className="text-xl md:text-2xl text-foreground/70 mb-12 max-w-3xl mx-auto animate-on-scroll">
              The only navigation app with real-time chat, multi-stop optimization, 
              and complete offline maps. More features than Google Maps, Apple Maps, and Waze combined.
            </p>

            {/* CTA Buttons */}
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4 mb-8 animate-on-scroll">
              <Button size="lg" className="bg-gradient-to-r from-[oklch(var(--gradient-from))] to-[oklch(var(--gradient-to))] text-white hover:opacity-90 transition-opacity text-lg px-8 py-6">
                <Download className="w-5 h-5 mr-2" />
                Download for iOS
              </Button>
              <Button size="lg" className="bg-gradient-to-r from-[oklch(var(--gradient-from))] to-[oklch(var(--gradient-to))] text-white hover:opacity-90 transition-opacity text-lg px-8 py-6">
                <Download className="w-5 h-5 mr-2" />
                Download for Android
              </Button>
            </div>
            
            {/* App Info Badge */}
            <div className="inline-flex items-center space-x-2 bg-muted px-4 py-2 rounded-full mb-16 animate-on-scroll">
              <Check className="w-4 h-4 text-primary" />
              <span className="text-sm font-medium">62 Professional Screens • iOS 16+ & Android 8+ • 9 Languages</span>
            </div>

            {/* Social Proof */}
            <div className="flex items-center justify-center gap-8 text-sm text-foreground/60 animate-on-scroll">
              <div className="flex items-center gap-2">
                <Star className="w-5 h-5 text-yellow-500 fill-yellow-500" />
                <span className="font-semibold text-foreground">4.9</span>
                <span>App Store Rating</span>
              </div>
              <div className="flex items-center gap-2">
                <Users className="w-5 h-5 text-primary" />
                <span className="font-semibold text-foreground">10K+</span>
                <span>Active Users</span>
              </div>
              <div className="flex items-center gap-2">
                <Download className="w-5 h-5 text-primary" />
                <span className="font-semibold text-foreground">50K+</span>
                <span>Downloads</span>
              </div>
            </div>
          </div>
        </div>

        {/* Decorative Elements */}
        <div className="absolute top-20 left-10 w-72 h-72 bg-primary/20 rounded-full blur-3xl"></div>
        <div className="absolute bottom-20 right-10 w-96 h-96 bg-[oklch(var(--gradient-to))]/20 rounded-full blur-3xl"></div>
      </section>

      {/* Features Grid */}
      <section id="features" className="py-20 bg-muted/30">
        <div className="container mx-auto px-6">
          <div className="text-center mb-16 animate-on-scroll">
            <h2 className="text-4xl md:text-5xl font-bold mb-4">
              Everything You Need.
              <br />
              <span className="text-primary">Nothing You Don't.</span>
            </h2>
            <p className="text-xl text-foreground/70 max-w-2xl mx-auto">
              Navi combines the best of Google Maps, Apple Maps, and Waze—then adds features they don't have.
            </p>
          </div>

          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-8">
            {/* Feature 1 */}
            <div className="bg-card p-8 rounded-2xl border border-border hover:border-primary/50 transition-all hover:shadow-lg animate-on-scroll">
              <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center mb-4">
                <MessageCircle className="w-6 h-6 text-primary" />
              </div>
              <h3 className="text-2xl font-bold mb-3">Real-Time Chat</h3>
              <p className="text-foreground/70 mb-4">
                Message friends while navigating. Share locations, ETAs, and coordinate meetups—all without leaving the app.
              </p>
              <div className="text-sm text-primary font-medium">Google & Apple don't have this →</div>
            </div>

            {/* Feature 2 */}
            <div className="bg-card p-8 rounded-2xl border border-border hover:border-primary/50 transition-all hover:shadow-lg animate-on-scroll">
              <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center mb-4">
                <MapPin className="w-6 h-6 text-primary" />
              </div>
              <h3 className="text-2xl font-bold mb-3">Multi-Stop Optimization</h3>
              <p className="text-foreground/70 mb-4">
                Plan routes with unlimited waypoints. Automatic optimization saves you time and fuel on every trip.
              </p>
              <div className="text-sm text-primary font-medium">Better than all competitors →</div>
            </div>

            {/* Feature 3 */}
            <div className="bg-card p-8 rounded-2xl border border-border hover:border-primary/50 transition-all hover:shadow-lg animate-on-scroll">
              <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center mb-4">
                <Wifi className="w-6 h-6 text-primary" />
              </div>
              <h3 className="text-2xl font-bold mb-3">Complete Offline Maps</h3>
              <p className="text-foreground/70 mb-4">
                Download entire regions for offline navigation. Works perfectly without internet connection.
              </p>
              <div className="text-sm text-primary font-medium">Full offline routing →</div>
            </div>

            {/* Feature 4 */}
            <div className="bg-card p-8 rounded-2xl border border-border hover:border-primary/50 transition-all hover:shadow-lg animate-on-scroll">
              <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center mb-4">
                <Camera className="w-6 h-6 text-primary" />
              </div>
              <h3 className="text-2xl font-bold mb-3">Speed Camera Alerts</h3>
              <p className="text-foreground/70 mb-4">
                Avoid tickets with advance warnings for speed cameras, hazards, and police. Community-powered alerts.
              </p>
              <div className="text-sm text-primary font-medium">Waze-level safety →</div>
            </div>

            {/* Feature 5 */}
            <div className="bg-card p-8 rounded-2xl border border-border hover:border-primary/50 transition-all hover:shadow-lg animate-on-scroll">
              <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center mb-4">
                <Car className="w-6 h-6 text-primary" />
              </div>
              <h3 className="text-2xl font-bold mb-3">Smart Parking</h3>
              <p className="text-foreground/70 mb-4">
                Find parking before you arrive. Save your spot with photo and notes. Never forget where you parked.
              </p>
              <div className="text-sm text-primary font-medium">Complete parking solution →</div>
            </div>

            {/* Feature 6 */}
            <div className="bg-card p-8 rounded-2xl border border-border hover:border-primary/50 transition-all hover:shadow-lg animate-on-scroll">
              <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center mb-4">
                <TrendingUp className="w-6 h-6 text-primary" />
              </div>
              <h3 className="text-2xl font-bold mb-3">Driving Analytics</h3>
              <p className="text-foreground/70 mb-4">
                Track trips, analyze driving behavior, earn achievements. Export data in 5 formats for insurance discounts.
              </p>
              <div className="text-sm text-primary font-medium">Unique to Navi →</div>
            </div>
          </div>
        </div>
      </section>

      {/* Pricing Section */}
      <section id="pricing" className="py-20">
        <div className="container mx-auto px-6">
          <div className="text-center mb-16 animate-on-scroll">
            <h2 className="text-4xl md:text-5xl font-bold mb-4">
              Simple, Transparent Pricing
            </h2>
            <p className="text-xl text-foreground/70">
              Start free. Upgrade when you need more.
            </p>
          </div>

          <div className="grid md:grid-cols-3 gap-8 max-w-6xl mx-auto">
            {/* Free Tier */}
            <div className="bg-card p-8 rounded-2xl border border-border animate-on-scroll">
              <h3 className="text-2xl font-bold mb-2">Free</h3>
              <div className="mb-6">
                <span className="text-4xl font-bold">$0</span>
                <span className="text-foreground/60">/forever</span>
              </div>
              <ul className="space-y-3 mb-8">
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>Turn-by-turn navigation</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>1 offline map region</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>Basic analytics</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>3 waypoints max</span>
                </li>
              </ul>
              <Button variant="outline" className="w-full">Get Started</Button>
            </div>

            {/* Premium Tier */}
            <div className="bg-gradient-to-br from-primary/10 to-[oklch(var(--gradient-to))]/10 p-8 rounded-2xl border-2 border-primary relative animate-on-scroll">
              <div className="absolute -top-4 left-1/2 -translate-x-1/2 bg-primary text-white px-4 py-1 rounded-full text-sm font-medium">
                Most Popular
              </div>
              <h3 className="text-2xl font-bold mb-2">Premium</h3>
              <div className="mb-6">
                <span className="text-4xl font-bold">$4.99</span>
                <span className="text-foreground/60">/month</span>
              </div>
              <ul className="space-y-3 mb-8">
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span className="font-medium">Everything in Free, plus:</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>Unlimited chat messaging</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>Unlimited waypoints</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>All export formats</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>Real-time parking availability</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>Advanced safety alerts</span>
                </li>
              </ul>
              <Button className="w-full bg-gradient-to-r from-[oklch(var(--gradient-from))] to-[oklch(var(--gradient-to))] text-white">
                Start Premium
              </Button>
            </div>

            {/* Pro Tier */}
            <div className="bg-card p-8 rounded-2xl border border-border animate-on-scroll">
              <h3 className="text-2xl font-bold mb-2">Pro</h3>
              <div className="mb-6">
                <span className="text-4xl font-bold">$9.99</span>
                <span className="text-foreground/60">/month</span>
              </div>
              <ul className="space-y-3 mb-8">
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span className="font-medium">Everything in Premium, plus:</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>API access</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>Fleet management</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>Insurance integration</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-5 h-5 text-primary mt-0.5 flex-shrink-0" />
                  <span>Priority support</span>
                </li>
              </ul>
              <Button variant="outline" className="w-full">Contact Sales</Button>
            </div>
          </div>
        </div>
      </section>

      {/* Testimonials */}
      <section id="testimonials" className="py-20 bg-muted/30">
        <div className="container mx-auto px-6">
          <div className="text-center mb-16 animate-on-scroll">
            <h2 className="text-4xl md:text-5xl font-bold mb-4">
              Loved by Thousands
            </h2>
            <p className="text-xl text-foreground/70">
              See what our users are saying
            </p>
          </div>

          <div className="grid md:grid-cols-3 gap-8 max-w-6xl mx-auto">
            <div className="bg-card p-6 rounded-2xl border border-border animate-on-scroll">
              <div className="flex gap-1 mb-4">
                {[...Array(5)].map((_, i) => (
                  <Star key={i} className="w-5 h-5 text-yellow-500 fill-yellow-500" />
                ))}
              </div>
              <p className="text-foreground/80 mb-4">
                "The multi-stop optimization saved me 30 minutes on my errands route. This app pays for itself!"
              </p>
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-primary/20 rounded-full flex items-center justify-center text-primary font-bold">
                  SM
                </div>
                <div>
                  <div className="font-semibold">Sarah Martinez</div>
                  <div className="text-sm text-foreground/60">Premium User</div>
                </div>
              </div>
            </div>

            <div className="bg-card p-6 rounded-2xl border border-border animate-on-scroll">
              <div className="flex gap-1 mb-4">
                {[...Array(5)].map((_, i) => (
                  <Star key={i} className="w-5 h-5 text-yellow-500 fill-yellow-500" />
                ))}
              </div>
              <p className="text-foreground/80 mb-4">
                "Finally, a navigation app with real chat! Coordinating with friends has never been easier."
              </p>
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-primary/20 rounded-full flex items-center justify-center text-primary font-bold">
                  JC
                </div>
                <div>
                  <div className="font-semibold">James Chen</div>
                  <div className="text-sm text-foreground/60">Pro User</div>
                </div>
              </div>
            </div>

            <div className="bg-card p-6 rounded-2xl border border-border animate-on-scroll">
              <div className="flex gap-1 mb-4">
                {[...Array(5)].map((_, i) => (
                  <Star key={i} className="w-5 h-5 text-yellow-500 fill-yellow-500" />
                ))}
              </div>
              <p className="text-foreground/80 mb-4">
                "Speed camera alerts saved me from 3 tickets already. Worth every penny of the premium subscription."
              </p>
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-primary/20 rounded-full flex items-center justify-center text-primary font-bold">
                  EP
                </div>
                <div>
                  <div className="font-semibold">Emily Parker</div>
                  <div className="text-sm text-foreground/60">Premium User</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Final CTA */}
      <section className="py-20">
        <div className="container mx-auto px-6">
          <div className="bg-gradient-to-br from-primary/10 to-[oklch(var(--gradient-to))]/10 rounded-3xl p-12 md:p-16 text-center border border-primary/20 animate-on-scroll">
            <h2 className="text-4xl md:text-5xl font-bold mb-6">
              Ready to Navigate Smarter?
            </h2>
            <p className="text-xl text-foreground/70 mb-8 max-w-2xl mx-auto">
              Join thousands of users who've upgraded their navigation experience. Download Navi today and discover the difference.
            </p>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
              <Button size="lg" className="bg-gradient-to-r from-[oklch(var(--gradient-from))] to-[oklch(var(--gradient-to))] text-white hover:opacity-90 transition-opacity text-lg px-8 py-6">
                <Download className="w-5 h-5 mr-2" />
                Download for Free
              </Button>
              <Button size="lg" variant="outline" className="text-lg px-8 py-6">
                View on App Store
                <ArrowRight className="w-5 h-5 ml-2" />
              </Button>
            </div>
            <p className="text-sm text-foreground/60 mt-6">
              Free forever. No credit card required. Upgrade anytime.
            </p>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-muted/30 py-12 border-t border-border">
        <div className="container mx-auto px-6">
          <div className="grid md:grid-cols-4 gap-8 mb-8">
            <div>
              <div className="flex items-center space-x-2 mb-4">
                <Navigation className="w-6 h-6 text-primary" />
                <span className="text-xl font-bold">{APP_TITLE}</span>
              </div>
              <p className="text-foreground/60 text-sm">
                The smartest way to navigate. More features than Google Maps, Apple Maps, and Waze combined.
              </p>
            </div>
            <div>
              <h4 className="font-semibold mb-4">Product</h4>
              <ul className="space-y-2 text-sm text-foreground/60">
                <li><a href="#features" className="hover:text-foreground transition-colors">Features</a></li>
                <li><a href="#pricing" className="hover:text-foreground transition-colors">Pricing</a></li>
                <li><a href="#" className="hover:text-foreground transition-colors">Download</a></li>
                <li><a href="#" className="hover:text-foreground transition-colors">Roadmap</a></li>
              </ul>
            </div>
            <div>
              <h4 className="font-semibold mb-4">Company</h4>
              <ul className="space-y-2 text-sm text-foreground/60">
                <li><a href="#" className="hover:text-foreground transition-colors">About</a></li>
                <li><a href="#" className="hover:text-foreground transition-colors">Blog</a></li>
                <li><a href="#" className="hover:text-foreground transition-colors">Careers</a></li>
                <li><a href="/support" className="hover:text-foreground transition-colors">Support</a></li>
              </ul>
            </div>
            <div>
              <h4 className="font-semibold mb-4">Legal</h4>
              <ul className="space-y-2 text-sm text-foreground/60">
                <li><a href="/privacy" className="hover:text-foreground transition-colors">Privacy Policy</a></li>
                <li><a href="/terms" className="hover:text-foreground transition-colors">Terms of Service</a></li>
                <li><a href="#" className="hover:text-foreground transition-colors">Cookie Policy</a></li>
              </ul>
            </div>
          </div>
          <div className="border-t border-border pt-8 flex flex-col md:flex-row justify-between items-center gap-4">
            <p className="text-sm text-foreground/60">
              © 2025 {APP_TITLE}. All rights reserved.
            </p>
            <div className="flex gap-4">
              <Shield className="w-5 h-5 text-foreground/40" />
              <span className="text-sm text-foreground/60">Privacy First • Data Ownership • No Tracking</span>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
