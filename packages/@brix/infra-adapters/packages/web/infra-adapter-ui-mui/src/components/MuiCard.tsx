/**
 * @file MUI Card Component
 * @description Material UI implementation of CardProps from UIAdapter contract.
 *              Content container with header, footer, and elevation support.
 * @module @brix/infra-adapter-ui-mui/components/MuiCard
 * @version 3.1.0
 *
 * [Design Principles]
 * - Direct mapping from CardProps to MUI Card API
 * - Supports title, subtitle, and header actions
 * - Configurable elevation and border styles
 * - Hoverable state for interactive cards
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is an atomic component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for content layout.
 */

import type { FC } from 'react';
import type { CardProps } from '@brix/runtime-sdk-api-web';
import Card from '@mui/material/Card';
import CardHeader from '@mui/material/CardHeader';
import CardContent from '@mui/material/CardContent';
import CardActions from '@mui/material/CardActions';
import CardActionArea from '@mui/material/CardActionArea';
import Typography from '@mui/material/Typography';

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Card Component
 *
 * <p>Material UI implementation of CardProps from UIAdapter contract.
 * Provides a flexible content container with optional header, footer,
 * and interactive states.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Configurable elevation (shadow depth)</li>
 *   <li>Header with title, subtitle, and actions</li>
 *   <li>Footer section for action buttons</li>
 *   <li>Hoverable state for clickable cards</li>
 *   <li>Bordered variant for flat designs</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Dashboard and list views use this via UIAdapter</li>
 *   <li>No direct import allowed in Plugin layer</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Basic card
 * const { Card } = useUI();
 *
 * <Card title="User Profile" subtitle="Basic Information">
 *   <p>Content here...</p>
 * </Card>
 *
 * // Clickable card with actions
 * <Card
 *   title="Document"
 *   elevation={2}
 *   hoverable
 *   onClick={handleOpen}
 *   headerActions={<IconButton><MoreVert /></IconButton>}
 *   footer={<Button>View Details</Button>}
 * >
 *   <p>Document preview...</p>
 * </Card>
 * ```
 *
 * @param props - CardProps from UIAdapter contract
 * @returns MUI Card component
 */
export const MuiCard: FC<CardProps> = ({
  title,
  subtitle,
  elevation = 1,
  hoverable = false,
  bordered = false,
  onClick,
  headerActions,
  footer,
  style,
  className,
  children,
}) => {
  // Determine if card should be clickable
  const isClickable = !!onClick || hoverable;

  // Build card styles
  // Bordered variant uses outline instead of shadow
  const cardStyle = {
    ...style,
    ...(bordered && {
      boxShadow: 'none',
      border: '1px solid rgba(0, 0, 0, 0.12)',
    }),
    ...(hoverable && {
      transition: 'box-shadow 0.2s ease-in-out, transform 0.1s ease-in-out',
      '&:hover': {
        boxShadow: 4,
        transform: 'translateY(-2px)',
      },
    }),
  };

  // Check if header should be rendered
  const hasHeader = title || subtitle || headerActions;

  /**
   * Renders the card content structure
   *
   * <p>Encapsulated in a function to support both clickable and static variants.</p>
   */
  const renderCardContent = () => (
    <>
      {/* Header section with title, subtitle, and actions */}
      {hasHeader && (
        <CardHeader
          title={
            typeof title === 'string' ? (
              <Typography variant="h6" component="h2">
                {title}
              </Typography>
            ) : (
              title
            )
          }
          subheader={
            typeof subtitle === 'string' ? (
              <Typography variant="body2" color="text.secondary">
                {subtitle}
              </Typography>
            ) : (
              subtitle
            )
          }
          action={headerActions}
        />
      )}

      {/* Main content area */}
      {children && <CardContent>{children}</CardContent>}

      {/* Footer section for actions */}
      {footer && <CardActions>{footer}</CardActions>}
    </>
  );

  // Render clickable card with CardActionArea
  if (isClickable && onClick) {
    // Type cast needed: CardActionArea renders as button but our contract uses div events
    // Cast through unknown for type safety since event interfaces differ
    const handleActionClick = onClick as unknown as React.MouseEventHandler<HTMLButtonElement>;
    return (
      <Card
        elevation={bordered ? 0 : elevation}
        className={className}
        sx={cardStyle}
      >
        <CardActionArea onClick={handleActionClick}>
          {renderCardContent()}
        </CardActionArea>
      </Card>
    );
  }

  // Render static card
  return (
    <Card
      elevation={bordered ? 0 : elevation}
      className={className}
      sx={cardStyle}
    >
      {renderCardContent()}
    </Card>
  );
};

export default MuiCard;
