import { useState } from "react";
import { trpc } from "@/lib/trpc";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Users, Shield, ShieldOff, Search } from "lucide-react";
import { toast } from "sonner";
import ProtectedRoute from "@/components/ProtectedRoute";
import { format } from "date-fns";

function AdminUsersContent() {
  const [search, setSearch] = useState("");
  const utils = trpc.useUtils();

  // Fetch all users
  const { data: users, isLoading } = trpc.admin.users.list.useQuery({
    search: search || undefined,
  });

  // Toggle admin role mutation
  const toggleAdmin = trpc.admin.users.toggleAdmin.useMutation({
    onSuccess: (data) => {
      utils.admin.users.list.invalidate();
      toast.success(data.message);
    },
    onError: (error) => {
      toast.error(`Failed to update user: ${error.message}`);
    },
  });

  const handleToggleAdmin = (userId: number, currentRole: string) => {
    const newRole = currentRole === "admin" ? "user" : "admin";
    const action = newRole === "admin" ? "promote to admin" : "remove admin access";
    
    if (confirm(`Are you sure you want to ${action} for this user?`)) {
      toggleAdmin.mutate({ userId, role: newRole as "user" | "admin" });
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b">
        <div className="container mx-auto py-6">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-3xl font-bold text-gray-900">User Management</h1>
              <p className="text-gray-600 mt-1">Manage user roles and permissions</p>
            </div>
            <div className="flex items-center gap-2">
              <Users className="w-8 h-8 text-primary" />
            </div>
          </div>
        </div>
      </div>

      <div className="container mx-auto py-8">
        {/* Search */}
        <Card className="mb-6">
          <CardContent className="pt-6">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
              <Input
                placeholder="Search by name or email..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-10"
              />
            </div>
          </CardContent>
        </Card>

        {/* Users List */}
        <Card>
          <CardHeader>
            <CardTitle>All Users</CardTitle>
            <CardDescription>
              {users ? `${users.length} total users` : "Loading..."}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="text-center py-12">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto"></div>
                <p className="text-gray-600 mt-4">Loading users...</p>
              </div>
            ) : users && users.length > 0 ? (
              <div className="space-y-4">
                {users.map((user) => (
                  <div
                    key={user.id}
                    className="flex items-center justify-between p-4 border rounded-lg hover:bg-gray-50 transition-colors"
                  >
                    <div className="flex-1">
                      <div className="flex items-center gap-3 mb-1">
                        <h3 className="font-semibold text-lg">{user.name || "Unnamed User"}</h3>
                        {user.role === "admin" ? (
                          <Badge className="bg-purple-100 text-purple-800 flex items-center gap-1">
                            <Shield className="w-3 h-3" />
                            Admin
                          </Badge>
                        ) : (
                          <Badge variant="secondary">User</Badge>
                        )}
                      </div>
                      <p className="text-sm text-gray-600">{user.email || "No email"}</p>
                      <div className="flex items-center gap-4 mt-2 text-xs text-gray-500">
                        <span>ID: {user.id}</span>
                        <span>Joined: {format(new Date(user.createdAt), "MMM d, yyyy")}</span>
                        <span>
                          Last sign in: {format(new Date(user.lastSignedIn), "MMM d, yyyy")}
                        </span>
                      </div>
                    </div>
                    <div>
                      <Button
                        variant={user.role === "admin" ? "destructive" : "default"}
                        size="sm"
                        onClick={() => handleToggleAdmin(user.id, user.role)}
                        disabled={toggleAdmin.isPending}
                        className="flex items-center gap-2"
                      >
                        {user.role === "admin" ? (
                          <>
                            <ShieldOff className="w-4 h-4" />
                            Remove Admin
                          </>
                        ) : (
                          <>
                            <Shield className="w-4 h-4" />
                            Make Admin
                          </>
                        )}
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-12">
                <Users className="w-16 h-16 text-gray-300 mx-auto mb-4" />
                <h3 className="text-lg font-semibold text-gray-900 mb-2">No users found</h3>
                <p className="text-gray-600">
                  {search ? "Try adjusting your search" : "No users have signed up yet"}
                </p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

export default function AdminUsers() {
  return (
    <ProtectedRoute requireAdmin={true}>
      <AdminUsersContent />
    </ProtectedRoute>
  );
}
