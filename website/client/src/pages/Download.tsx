import { Button } from "@/components/ui/button";
import { APP_TITLE } from "@/const";
import { 
  Download as DownloadIcon, 
  Smartphone, 
  Check,
  Apple,
  PlayCircle,
  Globe,
  Shield,
  Zap
} from "lucide-react";

export default function Download() {
  return (
    <div className="min-h-screen bg-background">
      {/* Navigation */}
      <nav className="fixed top-0 left-0 right-0 z-50 bg-background/80 backdrop-blur-xl border-b border-border/50">
        <div className="container mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <a href="/" className="flex items-center space-x-2">
              <Smartphone className="w-8 h-8 text-primary" />
              <span className="text-2xl font-bold bg-gradient-to-r from-[oklch(var(--gradient-from))] to-[oklch(var(--gradient-to))] bg-clip-text text-transparent">
                {APP_TITLE}
              </span>
            </a>
            <div className="hidden md:flex items-center space-x-8">
              <a href="/#features" className="text-foreground/70 hover:text-foreground transition-colors">Features</a>
              <a href="/#pricing" className="text-foreground/70 hover:text-foreground transition-colors">Pricing</a>
              <a href="/download" className="text-primary font-medium">Download</a>
            </div>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="pt-32 pb-20">
        <div className="container mx-auto px-6">
          <div className="max-w-4xl mx-auto text-center">
            <h1 className="text-5xl md:text-6xl font-bold mb-6">
              Download {APP_TITLE}
            </h1>
            <p className="text-xl text-foreground/70 mb-12">
              Available for iOS and Android. 62 professional screens, 9 languages, and completely free to start.
            </p>
          </div>
        </div>
      </section>

      {/* Download Cards */}
      <section className="pb-20">
        <div className="container mx-auto px-6">
          <div className="grid md:grid-cols-2 gap-8 max-w-5xl mx-auto">
            {/* iOS Card */}
            <div className="bg-card p-8 rounded-3xl border-2 border-border hover:border-primary/50 transition-all">
              <div className="flex items-center justify-center w-16 h-16 bg-gradient-to-br from-blue-500 to-blue-600 rounded-2xl mb-6 mx-auto">
                <Apple className="w-10 h-10 text-white" />
              </div>
              
              <h2 className="text-3xl font-bold text-center mb-4">Download for iOS</h2>
              
              <div className="space-y-3 mb-8">
                <div className="flex items-center gap-3">
                  <Check className="w-5 h-5 text-primary flex-shrink-0" />
                  <span>iPhone & iPad compatible</span>
                </div>
                <div className="flex items-center gap-3">
                  <Check className="w-5 h-5 text-primary flex-shrink-0" />
                  <span>Requires iOS 16.0 or later</span>
                </div>
                <div className="flex items-center gap-3">
                  <Check className="w-5 h-5 text-primary flex-shrink-0" />
                  <span>SwiftUI with native performance</span>
                </div>
                <div className="flex items-center gap-3">
                  <Check className="w-5 h-5 text-primary flex-shrink-0" />
                  <span>62 professionally designed screens</span>
                </div>
                <div className="flex items-center gap-3">
                  <Check className="w-5 h-5 text-primary flex-shrink-0" />
                  <span>Dark mode & accessibility support</span>
                </div>
              </div>

              <Button 
                size="lg" 
                className="w-full bg-gradient-to-r from-blue-500 to-blue-600 text-white hover:opacity-90 transition-opacity text-lg py-6"
              >
                <Apple className="w-5 h-5 mr-2" />
                Download on App Store
              </Button>

              <p className="text-sm text-center text-foreground/60 mt-4">
                Coming soon to the App Store
              </p>
            </div>

            {/* Android Card */}
            <div className="bg-card p-8 rounded-3xl border-2 border-border hover:border-primary/50 transition-all">
              <div className="flex items-center justify-center w-16 h-16 bg-gradient-to-br from-green-500 to-green-600 rounded-2xl mb-6 mx-auto">
                <PlayCircle className="w-10 h-10 text-white" />
              </div>
              
              <h2 className="text-3xl font-bold text-center mb-4">Download for Android</h2>
              
              <div className="space-y-3 mb-8">
                <div className="flex items-center gap-3">
                  <Check className="w-5 h-5 text-primary flex-shrink-0" />
                  <span>Android phones & tablets</span>
                </div>
                <div className="flex items-center gap-3">
                  <Check className="w-5 h-5 text-primary flex-shrink-0" />
                  <span>Requires Android 8.0 (API 26) or later</span>
                </div>
                <div className="flex items-center gap-3">
                  <Check className="w-5 h-5 text-primary flex-shrink-0" />
                  <span>Jetpack Compose modern UI</span>
                </div>
                <div className="flex items-center gap-3">
                  <Check className="w-5 h-5 text-primary flex-shrink-0" />
                  <span>62 professionally designed screens</span>
                </div>
                <div className="flex items-center gap-3">
                  <Check className="w-5 h-5 text-primary flex-shrink-0" />
                  <span>Material Design 3 theming</span>
                </div>
              </div>

              <Button 
                size="lg" 
                className="w-full bg-gradient-to-r from-green-500 to-green-600 text-white hover:opacity-90 transition-opacity text-lg py-6"
              >
                <PlayCircle className="w-5 h-5 mr-2" />
                Get it on Google Play
              </Button>

              <p className="text-sm text-center text-foreground/60 mt-4">
                Coming soon to Google Play Store
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Features Overview */}
      <section className="py-20 bg-muted/30">
        <div className="container mx-auto px-6">
          <div className="max-w-4xl mx-auto">
            <h2 className="text-4xl font-bold text-center mb-12">What's Included</h2>
            
            <div className="grid md:grid-cols-3 gap-8">
              <div className="text-center">
                <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center mx-auto mb-4">
                  <Smartphone className="w-6 h-6 text-primary" />
                </div>
                <h3 className="text-xl font-bold mb-2">62 Screens</h3>
                <p className="text-foreground/70">
                  Comprehensive navigation experience with every feature you need
                </p>
              </div>

              <div className="text-center">
                <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center mx-auto mb-4">
                  <Globe className="w-6 h-6 text-primary" />
                </div>
                <h3 className="text-xl font-bold mb-2">9 Languages</h3>
                <p className="text-foreground/70">
                  English, Spanish, French, German, Italian, Portuguese, Japanese, Korean, Chinese
                </p>
              </div>

              <div className="text-center">
                <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center mx-auto mb-4">
                  <Shield className="w-6 h-6 text-primary" />
                </div>
                <h3 className="text-xl font-bold mb-2">Privacy First</h3>
                <p className="text-foreground/70">
                  Your location data stays private. No tracking, no selling your data
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Technical Specs */}
      <section className="py-20">
        <div className="container mx-auto px-6">
          <div className="max-w-4xl mx-auto">
            <h2 className="text-4xl font-bold text-center mb-12">Technical Specifications</h2>
            
            <div className="grid md:grid-cols-2 gap-8">
              {/* iOS Specs */}
              <div className="bg-card p-6 rounded-2xl border border-border">
                <h3 className="text-2xl font-bold mb-4 flex items-center gap-2">
                  <Apple className="w-6 h-6" />
                  iOS Version
                </h3>
                <div className="space-y-2 text-foreground/70">
                  <p><strong>Language:</strong> Swift 5.9+</p>
                  <p><strong>UI Framework:</strong> SwiftUI</p>
                  <p><strong>Architecture:</strong> MVVM</p>
                  <p><strong>Minimum iOS:</strong> 16.0</p>
                  <p><strong>Maps:</strong> Mapbox iOS SDK</p>
                  <p><strong>Size:</strong> ~80 MB</p>
                </div>
              </div>

              {/* Android Specs */}
              <div className="bg-card p-6 rounded-2xl border border-border">
                <h3 className="text-2xl font-bold mb-4 flex items-center gap-2">
                  <PlayCircle className="w-6 h-6" />
                  Android Version
                </h3>
                <div className="space-y-2 text-foreground/70">
                  <p><strong>Language:</strong> Kotlin 1.9+</p>
                  <p><strong>UI Framework:</strong> Jetpack Compose</p>
                  <p><strong>Architecture:</strong> MVVM</p>
                  <p><strong>Minimum Android:</strong> 8.0 (API 26)</p>
                  <p><strong>Maps:</strong> Mapbox Android SDK</p>
                  <p><strong>Size:</strong> ~45 MB</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-20 bg-gradient-to-br from-[oklch(var(--gradient-from))] to-[oklch(var(--gradient-to))] text-white">
        <div className="container mx-auto px-6">
          <div className="max-w-3xl mx-auto text-center">
            <Zap className="w-16 h-16 mx-auto mb-6" />
            <h2 className="text-4xl md:text-5xl font-bold mb-6">
              Ready to Navigate Smarter?
            </h2>
            <p className="text-xl mb-8 opacity-90">
              Join thousands of users who have already made the switch to {APP_TITLE}.
            </p>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
              <Button size="lg" variant="secondary" className="text-lg px-8 py-6">
                <Apple className="w-5 h-5 mr-2" />
                Download for iOS
              </Button>
              <Button size="lg" variant="secondary" className="text-lg px-8 py-6">
                <PlayCircle className="w-5 h-5 mr-2" />
                Download for Android
              </Button>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-muted/50 py-12">
        <div className="container mx-auto px-6">
          <div className="text-center text-foreground/60">
            <p>© 2024 {APP_TITLE}. All rights reserved.</p>
            <div className="flex items-center justify-center gap-6 mt-4">
              <a href="/" className="hover:text-foreground transition-colors">Home</a>
              <a href="/#features" className="hover:text-foreground transition-colors">Features</a>
              <a href="/#pricing" className="hover:text-foreground transition-colors">Pricing</a>
              <a href="/download" className="hover:text-foreground transition-colors">Download</a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
