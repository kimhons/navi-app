import { Navigation, ChevronDown, Mail, MessageCircle, Search, AlertCircle, CheckCircle2, Loader2, Upload, X, File } from "lucide-react";
import { APP_TITLE } from "@/const";
import { Link } from "wouter";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { toast } from "sonner";
import { trpc } from "@/lib/trpc";

export default function Support() {
  const [openFaq, setOpenFaq] = useState<number | null>(0);
  const [searchQuery, setSearchQuery] = useState("");
  const [formData, setFormData] = useState({
    name: "",
    email: "",
    subject: "",
    message: ""
  });
  const [files, setFiles] = useState<File[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  const faqs = [
    {
      category: "Getting Started",
      questions: [
        {
          q: "How do I download offline maps?",
          a: "Go to Settings → Offline Maps → Add Region. Select your desired location on the map, choose the radius (1-50km), and tap Download. Free users can download 1 region, Premium users get unlimited downloads up to 5GB total storage."
        },
        {
          q: "Why isn't my location showing correctly?",
          a: "Make sure you've granted Navi location permissions in Settings → Privacy → Location Services → Navi. Choose 'While Using' for basic navigation or 'Always' for background navigation. Also ensure GPS is enabled and you have a clear view of the sky for best accuracy."
        },
        {
          q: "How do I share my location with friends?",
          a: "Tap the Share icon during navigation, select friends from your list, and choose how long to share (15 min, 1 hour, or until arrival). Friends will see your real-time location and ETA. You can stop sharing anytime from the sharing panel."
        }
      ]
    },
    {
      category: "Navigation & Routes",
      questions: [
        {
          q: "How do I add multiple stops to my route?",
          a: "Search for your first destination, then tap 'Add Stop' before starting navigation. You can add unlimited waypoints (Premium feature). Navi will automatically optimize the route order to save time and fuel. Drag stops to reorder manually if needed."
        },
        {
          q: "Can I navigate without internet?",
          a: "Yes! Download offline maps for your region first (Settings → Offline Maps). Once downloaded, Navi works completely offline for navigation, routing, and search within that region. Traffic data requires internet connection."
        },
        {
          q: "How do I avoid tolls or highways?",
          a: "Tap the route options button (three dots) after entering your destination. Toggle 'Avoid Tolls' or 'Avoid Highways' before starting navigation. Navi will recalculate the route accordingly."
        }
      ]
    },
    {
      category: "Features & Settings",
      questions: [
        {
          q: "What's the difference between Free and Premium?",
          a: "Free includes basic navigation and 1 offline map region. Premium ($4.99/mo) adds unlimited offline maps, unlimited waypoints, advanced analytics, real-time parking, speed camera alerts, and chat messaging. Pro ($9.99/mo) adds API access and fleet management."
        },
        {
          q: "How do speed camera alerts work?",
          a: "Navi uses community-reported speed camera locations to alert you in advance. You'll hear an audio warning and see a visual indicator 500m before each camera. Premium users get additional hazard alerts for police, accidents, and road closures."
        },
        {
          q: "Can I use Navi with CarPlay?",
          a: "Yes! Connect your iPhone to your car's CarPlay system. Navi will appear on your car's display with a simplified interface optimized for driving. All navigation features work seamlessly through CarPlay."
        }
      ]
    },
    {
      category: "Account & Billing",
      questions: [
        {
          q: "How do I cancel my subscription?",
          a: "Open iPhone Settings → [Your Name] → Subscriptions → Navi. Tap 'Cancel Subscription'. Your Premium features will continue until the end of your billing period. No refunds for partial months."
        },
        {
          q: "Can I get a refund?",
          a: "Refunds are handled by Apple. Go to reportaproblem.apple.com, sign in, find your Navi purchase, and request a refund within 30 days. Apple reviews all refund requests on a case-by-case basis."
        },
        {
          q: "How do I delete my account?",
          a: "Go to Settings → Account → Delete Account. This permanently deletes all your data including saved locations, trip history, and offline maps. This action cannot be undone."
        }
      ]
    },
    {
      category: "Troubleshooting",
      questions: [
        {
          q: "Why won't offline maps download?",
          a: "Check: 1) You have enough device storage (maps can be 100MB-2GB), 2) You're connected to WiFi if WiFi-only mode is enabled, 3) Your subscription allows downloads (Free = 1 region), 4) Mapbox services are operational at status.mapbox.com."
        },
        {
          q: "Voice guidance isn't working",
          a: "Check: 1) Volume is turned up, 2) Voice guidance isn't muted in Settings → Navigation → Voice Guidance, 3) Your device isn't in Silent mode, 4) Bluetooth audio is connected if using car speakers. Try toggling voice guidance off and on."
        },
        {
          q: "The app keeps crashing",
          a: "Try: 1) Force quit and restart the app, 2) Restart your iPhone, 3) Check for app updates in the App Store, 4) Clear offline maps cache in Settings → Offline Maps → Clear Cache, 5) Reinstall the app (your account data is saved in the cloud)."
        }
      ]
    }
  ];

  const troubleshootingGuides = [
    {
      title: "GPS Not Working",
      icon: <AlertCircle className="w-6 h-6 text-destructive" />,
      steps: [
        "Go to Settings → Privacy → Location Services and ensure it's enabled",
        "Find Navi in the list and set to 'While Using the App' or 'Always'",
        "Ensure you're outdoors with a clear view of the sky",
        "Restart your iPhone to refresh GPS connections",
        "Check if other navigation apps work (if not, it's a device issue)"
      ]
    },
    {
      title: "Offline Maps Not Downloading",
      icon: <AlertCircle className="w-6 h-6 text-yellow-600" />,
      steps: [
        "Check your device storage in Settings → General → iPhone Storage",
        "Ensure you're connected to WiFi (required for large downloads)",
        "Verify your subscription tier allows downloads (Free = 1 region)",
        "Try downloading a smaller region first (reduce radius)",
        "Check Mapbox status at status.mapbox.com for service issues"
      ]
    },
    {
      title: "Navigation Not Starting",
      icon: <AlertCircle className="w-6 h-6 text-orange-600" />,
      steps: [
        "Ensure location permissions are granted (see GPS troubleshooting)",
        "Check that you have an active internet connection (unless using offline maps)",
        "Verify the destination address is valid and recognized",
        "Try searching for the destination again or entering a different format",
        "Force quit and restart the app if the issue persists"
      ]
    },
    {
      title: "Premium Features Not Working",
      icon: <AlertCircle className="w-6 h-6 text-blue-600" />,
      steps: [
        "Verify your subscription is active in Settings → [Your Name] → Subscriptions",
        "Force quit Navi and reopen to refresh subscription status",
        "Go to Settings → Account → Restore Purchases",
        "Check that you're signed in with the same Apple ID used for purchase",
        "Contact support if features still don't work after 24 hours"
      ]
    }
  ];

  const filteredFaqs = faqs.map(category => ({
    ...category,
    questions: category.questions.filter(
      faq =>
        faq.q.toLowerCase().includes(searchQuery.toLowerCase()) ||
        faq.a.toLowerCase().includes(searchQuery.toLowerCase())
    )
  })).filter(category => category.questions.length > 0);

  const contactMutation = trpc.contact.submit.useMutation({
    onSuccess: (data) => {
      toast.success(data.message);
      setFormData({ name: "", email: "", subject: "", message: "" });
      setFiles([]);
      setUploadProgress(0);
      setIsSubmitting(false);
    },
    onError: (error) => {
      toast.error(error.message || "Failed to send message. Please try again.");
      setUploadProgress(0);
      setIsSubmitting(false);
    },
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!formData.name || !formData.email || !formData.subject || !formData.message) {
      toast.error("Please fill in all fields");
      return;
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      toast.error("Please enter a valid email address");
      return;
    }

    setIsSubmitting(true);

    try {
      // Upload files if any
      let fileUrls: string[] = [];
      if (files.length > 0) {
        setUploadProgress(10);
        
        // Upload files to S3
        for (let i = 0; i < files.length; i++) {
          const file = files[i];
          const formData = new FormData();
          formData.append('file', file);
          
          const response = await fetch('/api/upload', {
            method: 'POST',
            body: formData,
          });
          
          if (!response.ok) {
            throw new Error(`Failed to upload ${file.name}`);
          }
          
          const data = await response.json();
          fileUrls.push(data.url);
          
          // Update progress
          setUploadProgress(10 + ((i + 1) / files.length) * 40);
        }
        
        setUploadProgress(50);
      }

      // Submit via tRPC with file URLs
      contactMutation.mutate({
        ...formData,
        attachments: fileUrls,
      });
      
      setUploadProgress(100);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to upload files");
      setUploadProgress(0);
      setIsSubmitting(false);
    }
  };

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

      {/* Hero Section */}
      <section className="pt-32 pb-12 bg-gradient-to-br from-primary/10 to-[oklch(var(--gradient-to))]/10">
        <div className="container mx-auto px-6">
          <div className="max-w-3xl mx-auto text-center">
            <h1 className="text-4xl md:text-5xl font-bold mb-4">How Can We Help?</h1>
            <p className="text-xl text-foreground/70 mb-8">
              Search our knowledge base or contact our support team
            </p>
            
            {/* Search Bar */}
            <div className="relative max-w-2xl mx-auto">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-foreground/40" />
              <Input
                type="text"
                placeholder="Search for help..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-12 pr-4 py-6 text-lg"
              />
            </div>
          </div>
        </div>
      </section>

      {/* Quick Links */}
      <section className="py-12 border-b border-border">
        <div className="container mx-auto px-6">
          <div className="grid md:grid-cols-3 gap-6 max-w-4xl mx-auto">
            <a href="#faq" className="bg-card p-6 rounded-xl border border-border hover:border-primary/50 transition-all hover:shadow-lg">
              <MessageCircle className="w-8 h-8 text-primary mb-3" />
              <h3 className="font-semibold mb-2">FAQs</h3>
              <p className="text-sm text-foreground/70">Find quick answers to common questions</p>
            </a>
            <a href="#troubleshooting" className="bg-card p-6 rounded-xl border border-border hover:border-primary/50 transition-all hover:shadow-lg">
              <AlertCircle className="w-8 h-8 text-primary mb-3" />
              <h3 className="font-semibold mb-2">Troubleshooting</h3>
              <p className="text-sm text-foreground/70">Step-by-step guides to fix issues</p>
            </a>
            <a href="#contact" className="bg-card p-6 rounded-xl border border-border hover:border-primary/50 transition-all hover:shadow-lg">
              <Mail className="w-8 h-8 text-primary mb-3" />
              <h3 className="font-semibold mb-2">Contact Us</h3>
              <p className="text-sm text-foreground/70">Get help from our support team</p>
            </a>
          </div>
        </div>
      </section>

      {/* FAQ Section */}
      <section id="faq" className="py-20">
        <div className="container mx-auto px-6">
          <div className="max-w-4xl mx-auto">
            <h2 className="text-3xl font-bold mb-8 text-center">Frequently Asked Questions</h2>
            
            {filteredFaqs.length === 0 ? (
              <div className="text-center py-12">
                <p className="text-foreground/60">No results found for "{searchQuery}"</p>
              </div>
            ) : (
              <div className="space-y-8">
                {filteredFaqs.map((category, categoryIndex) => (
                  <div key={categoryIndex}>
                    <h3 className="text-xl font-semibold mb-4 text-primary">{category.category}</h3>
                    <div className="space-y-3">
                      {category.questions.map((faq, faqIndex) => {
                        const globalIndex = categoryIndex * 100 + faqIndex;
                        return (
                          <div
                            key={faqIndex}
                            className="bg-card border border-border rounded-lg overflow-hidden"
                          >
                            <button
                              onClick={() => setOpenFaq(openFaq === globalIndex ? null : globalIndex)}
                              className="w-full px-6 py-4 flex items-center justify-between text-left hover:bg-muted/30 transition-colors"
                            >
                              <span className="font-medium pr-4">{faq.q}</span>
                              <ChevronDown
                                className={`w-5 h-5 text-foreground/60 flex-shrink-0 transition-transform ${
                                  openFaq === globalIndex ? "rotate-180" : ""
                                }`}
                              />
                            </button>
                            {openFaq === globalIndex && (
                              <div className="px-6 pb-4 text-foreground/80 leading-relaxed">
                                {faq.a}
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </section>

      {/* Troubleshooting Guides */}
      <section id="troubleshooting" className="py-20 bg-muted/30">
        <div className="container mx-auto px-6">
          <div className="max-w-4xl mx-auto">
            <h2 className="text-3xl font-bold mb-8 text-center">Troubleshooting Guides</h2>
            
            <div className="grid md:grid-cols-2 gap-6">
              {troubleshootingGuides.map((guide, index) => (
                <div key={index} className="bg-card p-6 rounded-xl border border-border">
                  <div className="flex items-start gap-4 mb-4">
                    {guide.icon}
                    <h3 className="text-xl font-semibold">{guide.title}</h3>
                  </div>
                  <ol className="space-y-3">
                    {guide.steps.map((step, stepIndex) => (
                      <li key={stepIndex} className="flex items-start gap-3">
                        <span className="flex-shrink-0 w-6 h-6 bg-primary/10 text-primary rounded-full flex items-center justify-center text-sm font-semibold">
                          {stepIndex + 1}
                        </span>
                        <span className="text-sm text-foreground/80 pt-0.5">{step}</span>
                      </li>
                    ))}
                  </ol>
                </div>
              ))}
            </div>

            <div className="mt-8 bg-primary/10 p-6 rounded-xl border border-primary/20">
              <div className="flex items-start gap-3">
                <CheckCircle2 className="w-6 h-6 text-primary flex-shrink-0 mt-0.5" />
                <div>
                  <h4 className="font-semibold mb-2">Still having issues?</h4>
                  <p className="text-sm text-foreground/80 mb-4">
                    If these guides don't solve your problem, please contact our support team below. Include details about your device, iOS version, and what you were doing when the issue occurred.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Contact Form */}
      <section id="contact" className="py-20">
        <div className="container mx-auto px-6">
          <div className="max-w-2xl mx-auto">
            <div className="text-center mb-8">
              <h2 className="text-3xl font-bold mb-4">Contact Support</h2>
              <p className="text-foreground/70">
                Can't find what you're looking for? Send us a message and we'll get back to you within 24 hours.
              </p>
            </div>

            <form onSubmit={handleSubmit} className="bg-card p-8 rounded-xl border border-border">
              <div className="space-y-6">
                <div>
                  <label htmlFor="name" className="block text-sm font-medium mb-2">
                    Name *
                  </label>
                  <Input
                    id="name"
                    type="text"
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    placeholder="Your name"
                    required
                  />
                </div>

                <div>
                  <label htmlFor="email" className="block text-sm font-medium mb-2">
                    Email *
                  </label>
                  <Input
                    id="email"
                    type="email"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    placeholder="your.email@example.com"
                    required
                  />
                </div>

                <div>
                  <label htmlFor="subject" className="block text-sm font-medium mb-2">
                    Subject *
                  </label>
                  <Input
                    id="subject"
                    type="text"
                    value={formData.subject}
                    onChange={(e) => setFormData({ ...formData, subject: e.target.value })}
                    placeholder="Brief description of your issue"
                    required
                  />
                </div>

                <div>
                  <label htmlFor="message" className="block text-sm font-medium mb-2">
                    Message *
                  </label>
                  <Textarea
                    id="message"
                    value={formData.message}
                    onChange={(e) => setFormData({ ...formData, message: e.target.value })}
                    placeholder="Please describe your issue in detail..."
                    rows={6}
                    required
                  />
                </div>

                {/* File Upload */}
                <div>
                  <label className="block text-sm font-medium mb-2">
                    Attachments (Optional)
                  </label>
                  <div className="space-y-3">
                    <div className="border-2 border-dashed border-border rounded-lg p-6 text-center hover:border-primary/50 transition-colors">
                      <input
                        type="file"
                        id="file-upload"
                        multiple
                        accept="image/*,.pdf,.doc,.docx,.txt"
                        onChange={(e) => {
                          const newFiles = Array.from(e.target.files || []);
                          const validFiles = newFiles.filter(file => {
                            const maxSize = 10 * 1024 * 1024; // 10MB
                            if (file.size > maxSize) {
                              toast.error(`${file.name} is too large. Max size is 10MB.`);
                              return false;
                            }
                            return true;
                          });
                          setFiles([...files, ...validFiles]);
                          e.target.value = ''; // Reset input
                        }}
                        className="hidden"
                      />
                      <label
                        htmlFor="file-upload"
                        className="cursor-pointer flex flex-col items-center"
                      >
                        <Upload className="w-8 h-8 text-foreground/40 mb-2" />
                        <span className="text-sm font-medium text-foreground/70">
                          Click to upload or drag and drop
                        </span>
                        <span className="text-xs text-foreground/50 mt-1">
                          PNG, JPG, PDF, DOC (max 10MB each)
                        </span>
                      </label>
                    </div>

                    {/* File List */}
                    {files.length > 0 && (
                      <div className="space-y-2">
                        {files.map((file, index) => (
                          <div
                            key={index}
                            className="flex items-center justify-between bg-muted/50 p-3 rounded-lg"
                          >
                            <div className="flex items-center gap-3 flex-1 min-w-0">
                              <File className="w-4 h-4 text-primary flex-shrink-0" />
                              <div className="flex-1 min-w-0">
                                <p className="text-sm font-medium truncate">{file.name}</p>
                                <p className="text-xs text-foreground/60">
                                  {(file.size / 1024).toFixed(1)} KB
                                </p>
                              </div>
                            </div>
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              onClick={() => {
                                setFiles(files.filter((_, i) => i !== index));
                              }}
                              className="flex-shrink-0"
                            >
                              <X className="w-4 h-4" />
                            </Button>
                          </div>
                        ))}
                        <p className="text-xs text-foreground/60 text-center">
                          {files.length} file{files.length !== 1 ? 's' : ''} attached
                        </p>
                      </div>
                    )}
                  </div>
                </div>

                {/* Upload Progress */}
                {uploadProgress > 0 && uploadProgress < 100 && (
                  <div className="space-y-2">
                    <div className="flex justify-between text-sm">
                      <span>Uploading files...</span>
                      <span>{uploadProgress}%</span>
                    </div>
                    <div className="w-full bg-muted rounded-full h-2">
                      <div
                        className="bg-primary h-2 rounded-full transition-all duration-300"
                        style={{ width: `${uploadProgress}%` }}
                      />
                    </div>
                  </div>
                )}

                <Button
                  type="submit"
                  disabled={isSubmitting}
                  className="w-full bg-gradient-to-r from-[oklch(var(--gradient-from))] to-[oklch(var(--gradient-to))] text-white"
                >
                  {isSubmitting ? (
                    <>
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      Sending...
                    </>
                  ) : (
                    <>
                      <Mail className="w-4 h-4 mr-2" />
                      Send Message
                    </>
                  )}
                </Button>
              </div>
            </form>

            <div className="mt-8 text-center">
              <p className="text-sm text-foreground/60 mb-4">
                You can also reach us at:
              </p>
              <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
                <a href="mailto:support@navi.app" className="text-primary hover:underline flex items-center gap-2">
                  <Mail className="w-4 h-4" />
                  support@navi.app
                </a>
              </div>
            </div>
          </div>
        </div>
      </section>

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
