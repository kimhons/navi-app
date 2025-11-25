import { useState } from "react";
import { trpc } from "@/lib/trpc";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { X, Send, Paperclip, User, Mail, Calendar, MessageSquare, AlertCircle, CheckCircle2, Clock, XCircle } from "lucide-react";
import { formatDistanceToNow, format } from "date-fns";
import { toast } from "sonner";

interface TicketDetailProps {
  ticketId: number;
  onClose: () => void;
}

export default function TicketDetail({ ticketId, onClose }: TicketDetailProps) {
  const [responseMessage, setResponseMessage] = useState("");
  const [isInternal, setIsInternal] = useState(false);
  const [newStatus, setNewStatus] = useState<string>("");
  const [newPriority, setNewPriority] = useState<string>("");

  const utils = trpc.useUtils();

  // Fetch ticket details
  const { data: ticket, isLoading } = trpc.admin.tickets.get.useQuery({ id: ticketId });

  // Mutations
  const updateTicket = trpc.admin.tickets.update.useMutation({
    onSuccess: () => {
      utils.admin.tickets.get.invalidate({ id: ticketId });
      utils.admin.tickets.list.invalidate();
      utils.admin.tickets.stats.invalidate();
      toast.success("Ticket updated successfully");
    },
    onError: (error) => {
      toast.error(`Failed to update ticket: ${error.message}`);
    },
  });

  const addResponse = trpc.admin.tickets.addResponse.useMutation({
    onSuccess: () => {
      utils.admin.tickets.get.invalidate({ id: ticketId});
      setResponseMessage("");
      toast.success("Response added successfully");
    },
    onError: (error) => {
      toast.error(`Failed to add response: ${error.message}`);
    },
  });

  const handleStatusChange = (status: string) => {
    updateTicket.mutate({ id: ticketId, status: status as any });
  };

  const handlePriorityChange = (priority: string) => {
    updateTicket.mutate({ id: ticketId, priority: priority as any });
  };

  const handleSubmitResponse = () => {
    if (!responseMessage.trim()) {
      toast.error("Please enter a response message");
      return;
    }

    addResponse.mutate({
      ticketId,
      message: responseMessage,
      isInternal,
    });
  };

  const getStatusBadge = (status: string) => {
    const variants: Record<string, { variant: any; icon: any; label: string; color: string }> = {
      open: { variant: "default", icon: AlertCircle, label: "Open", color: "text-blue-600" },
      in_progress: { variant: "secondary", icon: Clock, label: "In Progress", color: "text-orange-600" },
      resolved: { variant: "outline", icon: CheckCircle2, label: "Resolved", color: "text-green-600" },
      closed: { variant: "outline", icon: XCircle, label: "Closed", color: "text-gray-600" },
    };

    const config = variants[status] || variants.open;
    const Icon = config.icon;

    return (
      <Badge variant={config.variant} className="flex items-center gap-1">
        <Icon className="w-3 h-3" />
        {config.label}
      </Badge>
    );
  };

  const getPriorityBadge = (priority: string) => {
    const colors: Record<string, string> = {
      low: "bg-gray-100 text-gray-800",
      medium: "bg-blue-100 text-blue-800",
      high: "bg-orange-100 text-orange-800",
      urgent: "bg-red-100 text-red-800",
    };

    return (
      <Badge className={colors[priority] || colors.medium}>
        {priority.charAt(0).toUpperCase() + priority.slice(1)}
      </Badge>
    );
  };

  if (isLoading) {
    return (
      <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
        <div className="bg-white rounded-lg p-8">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto"></div>
          <p className="text-gray-600 mt-4">Loading ticket details...</p>
        </div>
      </div>
    );
  }

  if (!ticket) {
    return (
      <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
        <div className="bg-white rounded-lg p-8">
          <p className="text-red-600">Ticket not found</p>
          <Button onClick={onClose} className="mt-4">Close</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50 overflow-auto">
      <div className="bg-white rounded-lg max-w-6xl w-full max-h-[95vh] overflow-hidden flex flex-col my-8">
        {/* Header */}
        <div className="border-b p-6 flex items-center justify-between bg-gray-50">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <h2 className="text-2xl font-bold">Ticket #{ticket.ticketNumber}</h2>
              {getStatusBadge(ticket.status)}
              {getPriorityBadge(ticket.priority)}
            </div>
            <p className="text-gray-600">{ticket.subject}</p>
          </div>
          <Button variant="ghost" size="icon" onClick={onClose}>
            <X className="w-5 h-5" />
          </Button>
        </div>

        <div className="flex-1 overflow-auto p-6">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Main Content */}
            <div className="lg:col-span-2 space-y-6">
              {/* Original Message */}
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <MessageSquare className="w-5 h-5" />
                    Original Message
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    <div className="flex items-start gap-3">
                      <div className="w-10 h-10 rounded-full bg-primary/10 flex items-center justify-center">
                        <User className="w-5 h-5 text-primary" />
                      </div>
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="font-semibold">{ticket.name}</span>
                          <span className="text-sm text-gray-500">{ticket.email}</span>
                        </div>
                        <p className="text-sm text-gray-600">
                          {formatDistanceToNow(new Date(ticket.createdAt), { addSuffix: true })}
                        </p>
                      </div>
                    </div>
                    <Separator />
                    <div className="prose max-w-none">
                      <p className="whitespace-pre-wrap">{ticket.message}</p>
                    </div>
                    {ticket.attachments && ticket.attachments.length > 0 && (
                      <>
                        <Separator />
                        <div>
                          <p className="text-sm font-medium mb-2 flex items-center gap-2">
                            <Paperclip className="w-4 h-4" />
                            Attachments ({ticket.attachments.length})
                          </p>
                          <div className="space-y-2">
                            {ticket.attachments.map((url: string, i: number) => (
                              <a
                                key={i}
                                href={url}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="block text-sm text-primary hover:underline"
                              >
                                📎 Attachment {i + 1}
                              </a>
                            ))}
                          </div>
                        </div>
                      </>
                    )}
                  </div>
                </CardContent>
              </Card>

              {/* Responses */}
              {ticket.responses && ticket.responses.length > 0 && (
                <Card>
                  <CardHeader>
                    <CardTitle>Responses ({ticket.responses.length})</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="space-y-4">
                      {ticket.responses.map((response: any) => (
                        <div key={response.id} className="border-l-2 border-gray-200 pl-4">
                          <div className="flex items-start gap-3 mb-2">
                            <div className="w-8 h-8 rounded-full bg-gray-100 flex items-center justify-center">
                              <User className="w-4 h-4 text-gray-600" />
                            </div>
                            <div className="flex-1">
                              <div className="flex items-center gap-2">
                                <span className="font-medium">
                                  {response.userId ? "Support Team" : ticket.name}
                                </span>
                                {response.isInternal === 1 && (
                                  <Badge variant="secondary" className="text-xs">Internal Note</Badge>
                                )}
                              </div>
                              <p className="text-xs text-gray-500">
                                {format(new Date(response.createdAt), "MMM d, yyyy 'at' h:mm a")}
                              </p>
                            </div>
                          </div>
                          <p className="text-sm whitespace-pre-wrap ml-11">{response.message}</p>
                        </div>
                      ))}
                    </div>
                  </CardContent>
                </Card>
              )}

              {/* Add Response */}
              <Card>
                <CardHeader>
                  <CardTitle>Add Response</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    <Textarea
                      placeholder="Type your response here..."
                      value={responseMessage}
                      onChange={(e) => setResponseMessage(e.target.value)}
                      rows={6}
                      className="resize-none"
                    />
                    <div className="flex items-center justify-between">
                      <label className="flex items-center gap-2 text-sm">
                        <input
                          type="checkbox"
                          checked={isInternal}
                          onChange={(e) => setIsInternal(e.target.checked)}
                          className="rounded"
                        />
                        Internal note (not visible to customer)
                      </label>
                      <Button
                        onClick={handleSubmitResponse}
                        disabled={addResponse.isPending}
                        className="flex items-center gap-2"
                      >
                        <Send className="w-4 h-4" />
                        Send Response
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>

            {/* Sidebar */}
            <div className="space-y-6">
              {/* Ticket Info */}
              <Card>
                <CardHeader>
                  <CardTitle className="text-lg">Ticket Information</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div>
                    <label className="text-sm font-medium text-gray-600 block mb-2">Status</label>
                    <Select value={ticket.status} onValueChange={handleStatusChange}>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="open">Open</SelectItem>
                        <SelectItem value="in_progress">In Progress</SelectItem>
                        <SelectItem value="resolved">Resolved</SelectItem>
                        <SelectItem value="closed">Closed</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  <div>
                    <label className="text-sm font-medium text-gray-600 block mb-2">Priority</label>
                    <Select value={ticket.priority} onValueChange={handlePriorityChange}>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="low">Low</SelectItem>
                        <SelectItem value="medium">Medium</SelectItem>
                        <SelectItem value="high">High</SelectItem>
                        <SelectItem value="urgent">Urgent</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  <Separator />

                  <div className="space-y-3 text-sm">
                    <div className="flex items-center gap-2">
                      <Calendar className="w-4 h-4 text-gray-400" />
                      <span className="text-gray-600">Created:</span>
                      <span className="font-medium">
                        {format(new Date(ticket.createdAt), "MMM d, yyyy")}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Clock className="w-4 h-4 text-gray-400" />
                      <span className="text-gray-600">Updated:</span>
                      <span className="font-medium">
                        {formatDistanceToNow(new Date(ticket.updatedAt), { addSuffix: true })}
                      </span>
                    </div>
                    {ticket.resolvedAt && (
                      <div className="flex items-center gap-2">
                        <CheckCircle2 className="w-4 h-4 text-green-600" />
                        <span className="text-gray-600">Resolved:</span>
                        <span className="font-medium">
                          {format(new Date(ticket.resolvedAt), "MMM d, yyyy")}
                        </span>
                      </div>
                    )}
                  </div>
                </CardContent>
              </Card>

              {/* Customer Info */}
              <Card>
                <CardHeader>
                  <CardTitle className="text-lg">Customer Information</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <div className="flex items-start gap-2">
                    <User className="w-4 h-4 text-gray-400 mt-0.5" />
                    <div>
                      <p className="text-gray-600">Name</p>
                      <p className="font-medium">{ticket.name}</p>
                    </div>
                  </div>
                  <div className="flex items-start gap-2">
                    <Mail className="w-4 h-4 text-gray-400 mt-0.5" />
                    <div>
                      <p className="text-gray-600">Email</p>
                      <p className="font-medium break-all">{ticket.email}</p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
