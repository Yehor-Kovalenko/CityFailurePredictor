# City Failure Predictor Frontend

A modern React TypeScript frontend for the City Failure Predictor system, built with Tailwind CSS.

## Features

- **Incident Management**: Create, view, update, and delete incidents
- **Real-time Status Updates**: Track incident status (Open, In Progress, Resolved)
- **Filtering**: Filter incidents by type (Fire, Water, Electricity, Roads, Accident, Flood) and status
- **Dashboard Analytics**: View statistics on total, open, in-progress, and resolved incidents
- **Geolocation Support**: Track incidents with coordinates
- **Responsive Design**: Mobile-friendly interface with Tailwind CSS

## Getting Started

### Prerequisites

- Node.js 18+ and npm/yarn

### Installation

```bash
cd frontend
npm install
```

### Development

```bash
npm run dev
```

The application will be available at `http://localhost:3000`

### Build

```bash
npm run build
```

### Environment Variables

Create a `.env.local` file in the frontend directory:

```env
REACT_APP_API_URL=http://localhost:8080
```

## Project Structure

```
src/
├── components/        # React components
├── services/         # API service
├── utils/            # Helper functions
├── types.ts          # TypeScript type definitions
├── constants.ts      # Constants and colors
├── App.tsx           # Main app component
├── main.tsx          # Entry point
└── index.css         # Global styles
```

## Available Scripts

- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm run preview` - Preview production build
- `npm run lint` - Run ESLint

## API Integration

The frontend integrates with the CityFailurePredictor backend API:

- GET `/incidents` - Fetch all incidents
- GET `/incidents/{id}` - Fetch specific incident
- POST `/incidents` - Create new incident
- PATCH `/incidents/{id}/status` - Update incident status
- DELETE `/incidents/{id}` - Delete incident
- GET `/incidents/statuses` - Get available statuses

## Technologies

- **React 18** - UI framework
- **TypeScript** - Type safety
- **Tailwind CSS** - Styling
- **Vite** - Build tool
- **Axios** - HTTP client
- **Lucide React** - Icons
- **date-fns** - Date formatting

## License

MIT
